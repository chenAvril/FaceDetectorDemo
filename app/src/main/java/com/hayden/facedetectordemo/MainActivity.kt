@file:Suppress("DEPRECATION")

package com.hayden.facedetectordemo

import android.Manifest
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.media.FaceDetector
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.TimeUtils
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        const val tag = "MainActivity"
        const val defaultThreshold = 0.915F

        val permissions: Array<String> = arrayOf(Manifest.permission.CAMERA)
        const val permissionReqCode = 1
    }


    /**
     * 引擎是否准备
     */
    private var enginePrepared: Boolean = false
    private lateinit var engineWrapper: EngineWrapper

    private var mCamera: Camera? = null
    private var cameraId: Int = Camera.CameraInfo.CAMERA_FACING_FRONT
    private val previewWidth: Int = 640
    private val previewHeight: Int = 480

    private val frameOrientation: Int = 7

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var factorX: Float = 0F
    private var factorY: Float = 0F

    private val detectionContext = newSingleThreadContext("detection")
    private var working: Boolean = false

    /**
     * 活体开关，默认false不开启
     */
    private var isLive = false

    /**
     * 如果集合的size是10，说明就是有人脸了
     */
    private var faceList = mutableListOf<Boolean>()

    private lateinit var surface: CircleSurfaceView
    private lateinit var tvState: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var liveSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasPermissions()) {
            init()
        } else {
            requestPermission()
        }
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun requestPermission() = requestPermissions(permissions, permissionReqCode)

    private fun init() {
        setContentView(R.layout.activity_main)
        surface = findViewById(R.id.surface)
        tvState = findViewById(R.id.tv_state)
        ivPreview = findViewById(R.id.iv_preview)
        liveSwitch = findViewById(R.id.sw)
        liveSwitch.setOnCheckedChangeListener { _, isChecked ->
            isLive = isChecked
        }

        Log.e(tag, "getDownloadCachePath : ${PathUtils.getDownloadCachePath()}")
        Log.e(tag, "getInternalAppCodeCacheDir : ${PathUtils.getInternalAppCodeCacheDir()}")
        Log.e(tag, "getInternalAppCachePath : ${PathUtils.getInternalAppCachePath()}")
        Log.e(tag, "getExternalAppCachePath : ${PathUtils.getExternalAppCachePath()}")

        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels

        surface.holder.let {
            it.setFormat(ImageFormat.NV21)
            it.addCallback(object : SurfaceHolder.Callback, Camera.PreviewCallback {
                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    if (mCamera == null) return

                    try {
                        mCamera?.stopPreview()
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                    }

                    val parameters = mCamera?.parameters
                    parameters?.setPreviewSize(previewWidth, previewHeight)

                    factorX = screenWidth / previewHeight.toFloat()
                    factorY = screenHeight / previewWidth.toFloat()

                    Log.e("chenlei","factorX--->$factorX")
                    Log.e("chenlei","factorY--->$factorY")

                    mCamera?.parameters = parameters

                    mCamera?.startPreview()
                    mCamera?.setPreviewCallback(this)

                    setCameraDisplayOrientation()
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    mCamera?.setPreviewCallback(null)
                    mCamera?.release()
                    mCamera = null
                }

                override fun surfaceCreated(holder: SurfaceHolder) {
                    try {
                        mCamera = Camera.open(cameraId)
                    } catch (e: Exception) {
                        cameraId = Camera.CameraInfo.CAMERA_FACING_BACK
                        mCamera = Camera.open(cameraId)
                    }

                    try {

                        // 放大相机预览
                        if (mCamera!!.parameters!!.isSmoothZoomSupported) {
                            val params = mCamera!!.parameters
                            val MAX = params.maxZoom;
                            if (MAX != 0) {
                                var zoomValue = params.zoom;
                                zoomValue += 3
                                params.zoom = zoomValue;
                                mCamera!!.parameters = params;
                            }

                        }
                        mCamera!!.setPreviewDisplay(surface.holder)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }

                override fun onPreviewFrame(data: ByteArray, camera: Camera) {
                    if (enginePrepared) {
                        if (!working) {
                            GlobalScope.launch(detectionContext) {
                                working = true

                                var hasFace = false
                                var confidence : Float = 0f
                                var currentBitmap: Bitmap? = null

                                if (isLive) {// 有活体检测的人脸识别
                                    val result = engineWrapper.detect(
                                        data,
                                        previewWidth,
                                        previewHeight,
                                        frameOrientation
                                    )
                                    hasFace = result.hasFace
                                    confidence = result.confidence

                                    if (hasFace) {
                                        currentBitmap = cameraToBitmap(data, camera)
                                    }
                                } else {// 安卓自带的人脸识别，但没有活体检测
                                    currentBitmap = cameraToBitmap(data, camera)
                                    val bitmapDetect: Bitmap =
                                        currentBitmap.copy(Bitmap.Config.RGB_565, true)

                                    val faces = arrayOfNulls<FaceDetector.Face>(1)
                                    val faceDetector =
                                        FaceDetector(bitmapDetect.width, bitmapDetect.height, 1)
                                    val face = faceDetector.findFaces(bitmapDetect, faces)
                                    if (face > 0) {
                                        hasFace = true

                                        val face1 = faces[0]!!
                                        confidence = face1.confidence()
                                        // 返回一个介于 0 和 1 之间的置信度因子。
                                        // 这表明所找到的实际上是一张脸的确定程度。高于 0.3 的置信系数通常就足够了。
//                                        Log.e(tag, "$face ====> ${face1.confidence()}")
                                    }
                                }

                                if(hasFace){
                                    if(isLive){// 活体
                                        if(confidence > defaultThreshold){
                                            faceList.add(true)
                                        }
                                    }else{ // 非活体
                                        faceList.add(true)
                                    }

                                }else{
                                    faceList.clear()
                                }
                                withContext(Dispatchers.Main) {
                                    if (hasFace) {
                                        tvState.text = "有人脸，可信度：${confidence}"
                                        if (currentBitmap != null) {
                                            ivPreview.setImageBitmap(currentBitmap)
                                        }
                                    } else {
                                        tvState.text = ""
                                    }
                                }

                                // TODO：此处可以对位图进行处理，如显示，保存等
                                // /data/user/0/com.hayden.flutter_project/cache/CAP608363337.jpg
                                if (faceList.size == 10) {


                                    val isSave = ImageUtils.save(
                                        currentBitmap,
                                        File(PathUtils.getInternalAppCachePath() + "/" + TimeUtils.getNowMills() + ".png"),
                                        Bitmap.CompressFormat.PNG
                                    )
                                    if (isSave) {
                                        finish()
                                    }
                                }

                                working = false
                            }
                        }
                    }
                }
            })
        }
    }

    /**
     * 预览的相机数据转成bitmap
     */
    private fun cameraToBitmap(data: ByteArray, camera: Camera): Bitmap {
        // 从相机预览获取图片
        val size: Camera.Size = camera.parameters.previewSize
        val image = YuvImage(data, ImageFormat.NV21, size.width, size.height, null)
        val stream = ByteArrayOutputStream();
        image.compressToJpeg(
            Rect(0, 0, size.width, size.height),
            80,
            stream
        )

        var bmp = BitmapFactory.decodeByteArray(
            stream.toByteArray(),
            0,
            stream.size()
        )

        // 压缩图片，长宽各减一半
        bmp = ImageUtils.compressBySampleSize(bmp, bmp.width / 2, bmp.height / 2)
        // 旋转图片
        bmp = ImageUtils.rotate(
            bmp, -90, (bmp.width / 2).toFloat(),
            (bmp.height / 2).toFloat()
        )

        stream.close()
        return bmp
    }

    private fun setCameraDisplayOrientation() {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val rotation = windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        mCamera!!.setDisplayOrientation(result)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionReqCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init()
            } else {
                Toast.makeText(this, "请授权相机权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        engineWrapper = EngineWrapper(assets)
        enginePrepared = engineWrapper.init()

        if (!enginePrepared) {
            Toast.makeText(this, "Engine init failed.", Toast.LENGTH_LONG).show()
        }

        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        Log.e(tag, " ====> onPause")
        if (mCamera != null){
            mCamera?.setPreviewCallback(null)
//            surface.holder.removeCallback()
            mCamera?.stopPreview()
            mCamera?.release()
            mCamera = null;
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engineWrapper.destroy()

        if (mCamera != null) {
            mCamera?.stopPreview();
            mCamera?.release();
            mCamera = null;
        }
        surface.destroyDrawingCache()
    }

}

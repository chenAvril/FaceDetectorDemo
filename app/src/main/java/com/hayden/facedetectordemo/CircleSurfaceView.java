package com.hayden.facedetectordemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.SurfaceView;

/**
 * 圆形SurfaceView
 */
public class CircleSurfaceView extends SurfaceView {

    public CircleSurfaceView(Context context) {
        super(context);
        initView(context, null);
    }

    public CircleSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public CircleSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context, attrs);
    }

    private float x, y, radius;
    private Path mCircleClipPath;

    private Paint sidelinePaint;
    private float sidelineWidth;
    private int sidelineColor;

    @SuppressLint("Recycle")
    private void initView(Context context, AttributeSet attrs) {
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.CircleSurfaceView);
        sidelineColor = array.getColor(R.styleable.CircleSurfaceView_sidelineColor,Color.parseColor("#8BC34A"));
        sidelineWidth = array.getDimension(R.styleable.CircleSurfaceView_sidelineWidth, 0);
        array.recycle();

        mCircleClipPath = new Path();

        sidelinePaint = new Paint();
        sidelinePaint.setColor(sidelineColor);
        sidelinePaint.setStrokeWidth(sidelineWidth);

        this.setFocusable(true);
        this.setFocusableInTouchMode(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        x = (float) getMeasuredWidth() / 2f;
        y = (float) getMeasuredHeight() / 2f;

        mCircleClipPath.reset();
        radius = Math.min(x, y);
        mCircleClipPath.addCircle(x, y, radius - sidelineWidth, Path.Direction.CCW);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawCircle(x, y, radius, sidelinePaint);
        canvas.clipPath(mCircleClipPath);
        super.draw(canvas);
    }
}
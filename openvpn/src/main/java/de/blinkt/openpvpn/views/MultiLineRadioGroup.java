/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.views;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioGroup;

import java.util.HashMap;
import java.util.Map;

public class MultiLineRadioGroup extends RadioGroup {
    private Map<View, Rect> viewRectMap;

    public MultiLineRadioGroup(Context context) {
        this(context, null);
    }

    public MultiLineRadioGroup(Context context, AttributeSet attrs) {
        super(context, attrs);

        viewRectMap = new HashMap<View, Rect>();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
        int widthMeasurement = MeasureSpec.getSize(widthMeasureSpec);
        int heightMeasurement = MeasureSpec.getSize(heightMeasureSpec);
        switch (getOrientation()){
            case HORIZONTAL:
                heightMeasurement = findHorizontalHeight(widthMeasureSpec, heightMeasureSpec);
                break;
            case VERTICAL:
                widthMeasurement = findVerticalWidth(widthMeasureSpec, heightMeasureSpec);
                break;
        }
        setMeasuredDimension(widthMeasurement, heightMeasurement);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        for(int x=0; x < count; ++x) {
            View button = getChildAt(x);
            Rect dims = viewRectMap.get(button);
            button.layout(dims.left, dims.top, dims.right, dims.bottom);
        }
    }

    private int findHorizontalHeight(int widthMeasureSpec, int heightMeasureSpec){
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        int maxRight = MeasureSpec.getSize(widthMeasureSpec) - getPaddingRight();

        // create MeasureSpecs to accommodate max space that RadioButtons can occupy
        int newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(maxRight - getPaddingLeft(),
                MeasureSpec.getMode(widthMeasureSpec));
        int newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                parentHeight - (getPaddingTop() + getPaddingBottom()),
                MeasureSpec.getMode(heightMeasureSpec));

        int nextLeft = getPaddingLeft();
        int nextTop = getPaddingTop();
        int maxRowHeight = 0;
        viewRectMap.clear();
        // measure and find placement for each RadioButton (results to be used in onLayout() stage)
        int count = getChildCount();
        for(int x=0; x < count; ++x){
            View button = getChildAt(x);
            measureChild(button, newWidthMeasureSpec, newHeightMeasureSpec);

            maxRowHeight = Math.max(maxRowHeight, button.getMeasuredHeight());

            // determine RadioButton placement
            int nextRight = nextLeft + button.getMeasuredWidth();
            if(nextRight > maxRight){ // if current button will exceed border on this row ...
                // ... move to next row
                nextLeft = getPaddingLeft();
                nextTop += maxRowHeight;

                // adjust for next row values
                nextRight = nextLeft + button.getMeasuredWidth();
                maxRowHeight = button.getMeasuredHeight();
            }

            int nextBottom = nextTop + button.getMeasuredHeight();
            viewRectMap.put(button, new Rect(nextLeft, nextTop, nextRight, nextBottom));

            // update nextLeft
            nextLeft = nextRight;
        }

        // height of RadioGroup is a natural by-product of placing all the children
        int idealHeight = nextTop + maxRowHeight + getPaddingBottom();
        switch(MeasureSpec.getMode(heightMeasureSpec)){
            case MeasureSpec.UNSPECIFIED:
                return idealHeight;
            case MeasureSpec.AT_MOST:
                return Math.min(idealHeight, parentHeight);
            case MeasureSpec.EXACTLY:
            default:
                return parentHeight;
        }
    }

    private int findVerticalWidth(int widthMeasureSpec, int heightMeasureSpec){
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxBottom = MeasureSpec.getSize(heightMeasureSpec) - getPaddingBottom();

        // create MeasureSpecs to accommodate max space that RadioButtons can occupy
        int newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                parentWidth - (getPaddingLeft() + getPaddingRight()),
                MeasureSpec.getMode(widthMeasureSpec));
        int newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(maxBottom - getPaddingTop(),
                MeasureSpec.getMode(heightMeasureSpec));

        int nextTop = getPaddingTop();
        int nextLeft = getPaddingLeft();
        int maxColWidth = 0;
        viewRectMap.clear();
        // measure and find placement for each RadioButton (results to be used in onLayout() stage)
        int count = getChildCount();
        for(int x=0; x < count; ++x){
            View button = getChildAt(x);
            measureChild(button, newWidthMeasureSpec, newHeightMeasureSpec);

            maxColWidth = Math.max(maxColWidth, button.getMeasuredWidth());

            // determine RadioButton placement
            int nextBottom = nextTop + button.getMeasuredHeight();
            if(nextBottom > maxBottom){ // if current button will exceed border for this column ...
                // ... move to next column
                nextTop = getPaddingTop();
                nextLeft += maxColWidth;

                // adjust for next row values
                nextBottom = nextTop + button.getMeasuredHeight();
                maxColWidth = button.getMeasuredWidth();
            }

            int nextRight = nextLeft + button.getMeasuredWidth();
            viewRectMap.put(button, new Rect(nextLeft, nextTop, nextRight, nextBottom));

            // update nextTop
            nextTop = nextBottom;
        }

        // width of RadioGroup is a natural by-product of placing all the children
        int idealWidth = nextLeft + maxColWidth + getPaddingRight();
        switch(MeasureSpec.getMode(widthMeasureSpec)){
            case MeasureSpec.UNSPECIFIED:
                return idealWidth;
            case MeasureSpec.AT_MOST:
                return Math.min(idealWidth, parentWidth);
            case MeasureSpec.EXACTLY:
            default:
                return parentWidth;
        }
    }
}
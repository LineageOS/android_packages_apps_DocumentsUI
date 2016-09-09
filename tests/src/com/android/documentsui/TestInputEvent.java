package com.android.documentsui;

import android.graphics.Point;
import android.support.v7.widget.RecyclerView;

import com.android.documentsui.dirlist.DocumentDetails;

public class TestInputEvent implements Events.InputEvent {

    public boolean mouseEvent;
    public boolean primaryButtonPressed;
    public boolean secondaryButtonPressed;
    public boolean shiftKeyDow;
    public boolean ctrlKeyDow;
    public boolean actionDown;
    public boolean actionUp;
    public boolean actionMove;
    public Point location;
    public Point rawLocation;
    public int position = Integer.MIN_VALUE;
    public DocumentDetails details;

    public TestInputEvent() {}

    public TestInputEvent(int position) {
        this.position = position;
    }

    @Override
    public boolean isMouseEvent() {
        return mouseEvent;
    }

    @Override
    public boolean isPrimaryButtonPressed() {
        return primaryButtonPressed;
    }

    @Override
    public boolean isSecondaryButtonPressed() {
        return secondaryButtonPressed;
    }

    @Override
    public boolean isShiftKeyDown() {
        return shiftKeyDow;
    }

    @Override
    public boolean isCtrlKeyDown() {
        return ctrlKeyDow;
    }

    @Override
    public boolean isActionDown() {
        return actionDown;
    }

    @Override
    public boolean isActionUp() {
        return actionUp;
    }

    @Override
    public boolean isActionMove() {
        return actionMove;
    }

    @Override
    public Point getOrigin() {
        return location;
    }

    @Override
    public float getX() {
        return location.x;
    }

    @Override
    public float getY() {
        return location.y;
    }

    @Override
    public float getRawX() {
        return rawLocation.x;
    }

    @Override
    public float getRawY() {
        return rawLocation.y;
    }

    @Override
    public boolean isOverItem() {
        return position != Integer.MIN_VALUE && position != RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemPosition() {
        return position;
    }

    @Override
    public DocumentDetails getDocumentDetails() {
        return details;
    }

    @Override
    public void close() {}

    public static TestInputEvent tap(int position) {
        return new TestInputEvent(position);
    }

    public static TestInputEvent shiftTap(int position) {
        TestInputEvent e = new TestInputEvent(position);
        e.shiftKeyDow = true;
        return e;
    }

    public static TestInputEvent click(int position) {
        TestInputEvent e = new TestInputEvent(position);
        e.mouseEvent = true;
        return e;
    }

    public static TestInputEvent rightClick(int position) {
        TestInputEvent e = new TestInputEvent(position);
        e.mouseEvent = true;
        e.secondaryButtonPressed = true;
        return e;
    }

    public static TestInputEvent shiftClick(int position) {
        TestInputEvent e = new TestInputEvent(position);
        e.mouseEvent = true;
        e.shiftKeyDow = true;
        return e;
    }
}

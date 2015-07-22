package org.mewx.wenku8.reader.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import org.mewx.wenku8.reader.setting.WenkuReaderSettingV1;
import org.mewx.wenku8.util.LightTool;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/7/8.
 *
 * Implement whole view of page, and use full screen page size.
 *
 * Default Elements:
 *  - Top: ChapterTitle, WIFI/DC
 *  - Bot: Battery, Paragraph/All, CurrentTime
 *
 * Click Elements:
 *  - Top: NovelTitle
 *  - Bot: ToolBar
 */
public class WenkuReaderPageView extends View {
    // enum
    public enum LOADING_DIRECTION {
        FORWARDS, // go to next page
        CURRENT, // get this page
        BACKWARDS // go to previous page
    }

    // class
    private class LineInfo {
        WenkuReaderLoader.ElementType type;
        String text;
    }
    List<LineInfo> lineInfoList;

    // core variables
    static private String sampleText = "轻";
    static private WenkuReaderLoader mLoader;
    static private WenkuReaderSettingV1 mSetting;
    static private int pxLineDistance, pxParagraphDistance, pxParagraphEdgeDistance, pxPageEdgeDistance, pxWidgetHeight;
    private Point screenSize, textAreaSize;
    static private Typeface typeface;
    static private TextPaint textPaint, widgetTextPaint;
    static private int fontHeight, widgetFontHeihgt;
    private int lineCount;

    // vars
    private int firstLineIndex;
    private int firstWordIndex;
    private int lastLineIndex;
    private int lastWordIndex; // last paragraph's last word's index

    // view components (battery, page number, etc.)


    // useless constructs
//    public WenkuReaderPageView(Context context) {
//        super(context);
//        Log.e("MewX", "-- view: construct 1");
//    }
//
//    public WenkuReaderPageView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        Log.e("MewX", "-- view: construct 2");
//    }
//
//    public WenkuReaderPageView(Context context, AttributeSet attrs, int defStyleAttr) {
//        super(context, attrs, defStyleAttr);
//        Log.e("MewX", "-- view: construct 3");
//    }

    /**
     * Set view static variables, before first onDraw()
     * @param wrl loader
     * @param wrs setting
     */
    static public void setViewComponents(WenkuReaderLoader wrl, WenkuReaderSettingV1 wrs) {
        mLoader = wrl;
        mSetting = wrs;
        pxLineDistance = LightTool.dip2px(MyApp.getContext(), mSetting.getLineDistance());
        pxParagraphDistance = LightTool.dip2px(MyApp.getContext(), mSetting.getParagraphDistance());
        pxParagraphEdgeDistance = LightTool.dip2px(MyApp.getContext(), mSetting.getParagraghEdgeDistance());
        pxPageEdgeDistance = LightTool.dip2px(MyApp.getContext(), mSetting.getPageEdgeDistance());
        pxWidgetHeight = LightTool.dip2px(MyApp.getContext(), mSetting.widgetHeight);

        // calc general var
        typeface = Typeface.createFromAsset(MyApp.getContext().getAssets(), "fonts/fzss-gbk.ttf"); // use font
        textPaint = new TextPaint();
        textPaint.setColor(mSetting.inDayMode ? mSetting.fontColorDark : mSetting.fontColorLight);
        textPaint.setTextSize(LightTool.sp2px(MyApp.getContext(), (float) mSetting.getFontSize()));
        textPaint.setTypeface(typeface);
        textPaint.setAntiAlias(true);
        fontHeight = (int) textPaint.measureText(sampleText); //(int) textPaint.getTextSize(); // in "px"
        widgetTextPaint = new TextPaint();
        widgetTextPaint.setColor(mSetting.inDayMode ? mSetting.fontColorDark : mSetting.fontColorLight);
        widgetTextPaint.setTextSize(LightTool.sp2px(MyApp.getContext(), (float) mSetting.widgetTextSize));
        widgetTextPaint.setAntiAlias(true);
        widgetFontHeihgt = (int) textPaint.measureText(sampleText);
    }

    /**
     * This function init the view class。
     * Notice: (-1, -1), (-1, 0), (0, -1) means first page.
     * @param context current context, should be WenkuReaderActivity
     * @param lineIndex if FORWARDS, this is the last index of last page;
     *              if CURRENT, this is the first index of this page;
     *              if BACKWARDS, this is the first index of last page;
     * @param directionForward get next or get previous
     */
    public WenkuReaderPageView(Context context, int lineIndex, int wordIndex, LOADING_DIRECTION directionForward) {
        super(context);
        Log.e("MewX", "-- view: construct my");
        // TODO: split a setter, this function is useless, so set everything first before system's getView

        lineInfoList = new ArrayList<>();
        mLoader.setCurrentIndex(lineIndex);

        // get environmental vars
        // TODO: this should use actual layout size
        screenSize = LightTool.getRealScreenSize(getContext());
        textAreaSize = new Point(screenSize.x - 2 * (pxPageEdgeDistance + pxParagraphEdgeDistance),
                screenSize.y - 2 * (pxPageEdgeDistance + pxWidgetHeight));

        // save vars, calc all ints
        switch (directionForward) {
            case FORWARDS:
                if(wordIndex + 1 < mLoader.getCurrentAsString().length()) {
                    firstLineIndex = lineIndex;
                    if(lineIndex == 0 && wordIndex == 0)
                        firstWordIndex = 0;
                    else
                        firstWordIndex = wordIndex + 1;
                }
                else if(lineIndex + 1 < mLoader.getElementCount()){
                    firstLineIndex = lineIndex + 1;
                    firstWordIndex = 0;
                }
                else {
                    Log.e("MewX", "-- view: end construct A, just return");
                    return;
                }
                mLoader.setCurrentIndex(firstLineIndex);
                calcFromFirst();
                break;

            case CURRENT:
                firstLineIndex = lineIndex;
                firstWordIndex = wordIndex;
                mLoader.setCurrentIndex(firstLineIndex);
                calcFromFirst();
                break;

            case BACKWARDS:
                // TODO: fit first and last
                if(wordIndex > 0) {
                    lastLineIndex = lineIndex;
                    lastWordIndex = wordIndex - 1;
                }
                else if(lineIndex > 0) {
                    lastLineIndex = lineIndex - 1;
                    lastWordIndex = mLoader.getStringLength(lastLineIndex) - 1;
                }

                // TODO: firstLineIndex firstWordIndex; and last values changeable
                mLoader.setCurrentIndex(lastLineIndex);
                calcFromLast();
                break;
        }

        for(LineInfo li : lineInfoList)
            Log.e("MewX", "get: " + li.text);

    }

    /**
     * Calc page from first to last.
     * firstLineIndex & firstWordIndex set.
     */
    private void calcFromFirst() {
        int widthSum = 0;
        int heightSum = fontHeight;
        String tempText = "";

        Log.e("MewX", "firstLineIndex = " + firstLineIndex + "; firstWordIndex = " + firstWordIndex);
        for(int curLineIndex = firstLineIndex, curWordIndex = firstWordIndex; curLineIndex < mLoader.getElementCount(); ) {
            // init paragraph head vars
            if(curWordIndex == 0 && mLoader.getCurrentType() == WenkuReaderLoader.ElementType.TEXT) {
                // leading space
                widthSum = 2 * fontHeight;
                tempText = "　　";
            }
            else if(mLoader.getCurrentType() == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT) {
                if(lineInfoList.size() != 0) {
                    // end a page first
                    lastLineIndex = mLoader.getCurrentIndex() - 1;
                    mLoader.setCurrentIndex(lastLineIndex);
                    lastWordIndex = mLoader.getCurrentAsString().length() - 1;
                    break;
                }

                // one image on page
                lastLineIndex = firstLineIndex = mLoader.getCurrentIndex();
                firstWordIndex = 0;
                lastWordIndex = mLoader.getCurrentAsString().length() - 1;
                LineInfo li = new LineInfo();
                li.type = WenkuReaderLoader.ElementType.IMAGE_DEPENDENT;
                li.text = mLoader.getCurrentAsString();
                lineInfoList.add(li);
                break;
            }

            // get a record of line
            if(mLoader.getCurrentAsString() == null || mLoader.getCurrentAsString().length() == 0) {
                Log.e("MewX", "empty string! in " + curLineIndex + "(" + curWordIndex + ")");
                curWordIndex = 0;
                if(curLineIndex >= mLoader.getElementCount()) {
                    // out of bounds
                    break;
                }
                mLoader.setCurrentIndex(++ curLineIndex);
            }
            WenkuReaderLoader.ElementType type = mLoader.getCurrentType();
            String temp = mLoader.getCurrentAsString().charAt(curWordIndex) + "";
            int tempWidth = (int) textPaint.measureText(temp);

            // Line full?
            if(widthSum + tempWidth > textAreaSize.x) {
                // wrap line, save line
                LineInfo li = new LineInfo();
                li.type = WenkuReaderLoader.ElementType.TEXT;
                li.text = tempText;
                lineInfoList.add(li);
                heightSum += pxLineDistance;

                // change vars for next line
                if(heightSum + fontHeight > textAreaSize.y) {
                    // reverse one index
                    if(curWordIndex > 0) {
                        lastLineIndex = curLineIndex;
                        lastWordIndex = curWordIndex - 1;
                    }
                    else if(curLineIndex > 0) {
                        mLoader.setCurrentIndex(-- curLineIndex);
                        lastLineIndex = curLineIndex;
                        lastWordIndex = mLoader.getCurrentAsString().length() - 1;
                    }
                    else {
                        lastLineIndex = lastWordIndex = 0;
                    }
                    break; // height overflow
                }

                // height acceptable
                tempText = temp;
                widthSum = tempWidth;
                heightSum += fontHeight;
            }
            else {
                tempText = tempText + temp;
                widthSum += tempWidth;
            }

            // String end?
            if(curWordIndex + 1 >= mLoader.getCurrentAsString().length()) {
                // next paragraph, wrap line
                LineInfo li = new LineInfo();
                li.type = WenkuReaderLoader.ElementType.TEXT;
                li.text = tempText;
                lineInfoList.add(li);
                heightSum += pxParagraphDistance;

                // height not acceptable
                if(heightSum + fontHeight > textAreaSize.y) {
                    lastLineIndex = mLoader.getCurrentIndex();
                    lastWordIndex = mLoader.getCurrentAsString().length() - 1;
                    break; // height overflow
                }

                // height acceptable
                heightSum += fontHeight;
                widthSum = 0;
                tempText = "";
                curWordIndex = 0;
                if(curLineIndex + 1 >= mLoader.getElementCount()) {
                    // out of bounds
                    lastLineIndex = curLineIndex;
                    lastWordIndex = mLoader.getCurrentAsString().length() - 1;
                    break;
                }
                mLoader.setCurrentIndex(++ curLineIndex);
            }
            else {
                curWordIndex ++;
            }
        }
    }

    /**
     * Calc page from last to first
     * lastLineIndex & lastWordIndex set.
     */
    private void calcFromLast() {

        int heightSum = 0;
        boolean isFirst = true;
        mLoader.setCurrentIndex(lastLineIndex);

        LineLoop:
        for(int curLineIndex = lastLineIndex, curWordIndex = lastWordIndex; curLineIndex >= 0; ) {
            // calc curLine to curWord(contained), make a String list
            WenkuReaderLoader.ElementType curType = mLoader.getCurrentType();
            String curString = mLoader.getCurrentAsString();

            // TODO: special to image
            if(curType == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT && lineInfoList.size() != 0) {
                Log.e("MewX", "jump 1");
                firstLineIndex = curLineIndex + 1;
                firstWordIndex = 0;
                mLoader.setCurrentIndex(firstLineIndex);
                lineInfoList = new ArrayList<>();
                calcFromFirst();
                break;
            }
            else if(curType == WenkuReaderLoader.ElementType.IMAGE_DEPENDENT) {
                // one image on page
                lastLineIndex = firstLineIndex = mLoader.getCurrentIndex();
                firstWordIndex = 0;
                lastWordIndex = mLoader.getCurrentAsString().length() - 1;
                LineInfo li = new LineInfo();
                li.type = WenkuReaderLoader.ElementType.IMAGE_DEPENDENT;
                li.text = mLoader.getCurrentAsString();
                lineInfoList.add(li);
                break;
            }

            int tempWidth = 0;
            List<LineInfo> curList = new ArrayList<>();
            String temp = "";
            for(int i = 0; i < curString.length(); ) {
                if(i == 0) {
                    tempWidth += fontHeight + fontHeight;
                    temp = "　　";
                }

                String c = curString.charAt(i) + "";
                int width = (int) textPaint.measureText(c);
                if(tempWidth + width > textAreaSize.x) {
                    // save line to next
                    LineInfo li = new LineInfo();
                    li.type = WenkuReaderLoader.ElementType.TEXT;
                    li.text = temp;
                    curList.add(li);

                    // fit needs
                    if(i >= curWordIndex) break;

                    // goto next round
                    tempWidth = 0;
                    temp = "";
                    continue;
                }
                else {
                    temp = temp + c;
                    tempWidth += width;
                    i ++;
                }

                // string end
                if(i == curString.length()) {
                    LineInfo li = new LineInfo();
                    li.type = WenkuReaderLoader.ElementType.TEXT;
                    li.text = temp;
                    curList.add(li);
                }
            }

            // reverse to add to lineInfoList, full to break, image to do calcFromFirst then break
            for(int i = curList.size() - 1; i >= 0; i --) {
                if(isFirst)
                    isFirst = false;
                else if(i == curList.size() - 1)
                    heightSum += pxParagraphDistance;
                else
                    heightSum += pxLineDistance;

                heightSum += fontHeight;
                if(heightSum > textAreaSize.y) {
                    // calc first index
                    int indexCount = -2;
                    for(int j = 0; j <= i; j ++) indexCount += curList.get(j).text.length();
                    firstLineIndex = curLineIndex;
                    firstWordIndex = indexCount + 1;

                    // out of index
                    if(firstWordIndex + 1 >= curString.length()) {
                        firstLineIndex = curLineIndex + 1;
                        firstWordIndex = 0;
                    }
                    break LineLoop;
                }
                lineInfoList.add(0, curList.get(i));
            }
            for(LineInfo li : lineInfoList)
                Log.e("MewX", "full: " + li.text);

            // not full to continue, set curWord as last index of the string
            if(curLineIndex - 1 >= 0) {
                mLoader.setCurrentIndex(-- curLineIndex);
                curWordIndex = mLoader.getCurrentAsString().length();
            }
            else {
                Log.e("MewX", "jump 2");
                firstLineIndex = 0;
                firstWordIndex = 0;
                mLoader.setCurrentIndex(firstLineIndex);
                lineInfoList = new ArrayList<>();
                calcFromFirst();
                break;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //canvas.drawLine(0.0f, 0.0f, 320.0f, 320.0f, new Paint()); // px
        if(mSetting == null || mLoader == null) return;
        Log.e("MewX", "onDraw()");

        // draw background
        Paint paintBackground = new Paint();
        paintBackground.setColor(mSetting.inDayMode ? mSetting.bgColorLight : mSetting.bgColorDark);
        canvas.drawRect(0, 0, screenSize.x, screenSize.y, paintBackground);
//        canvas.drawLine(pxParagraphEdgeDistance + pxPageEdgeDistance, pxPageEdgeDistance + pxWidgetHeight,
//                textAreaSize.x + pxParagraphEdgeDistance + pxPageEdgeDistance,
//                textAreaSize.y + pxPageEdgeDistance + pxWidgetHeight, new Paint()); // px

        // draw divider
        Paint paintDivider = new Paint();
        paintDivider.setColor(getContext().getResources().getColor(R.color.dlgDividerColor));
        canvas.drawLine(1, 1, 1, screenSize.y - 1, paintDivider);
        canvas.drawLine(screenSize.x - 1, 1, screenSize.x - 1, screenSize.y - 1, paintDivider);

        // draw widgets
        canvas.drawText(mLoader.getChapterName(), pxPageEdgeDistance, screenSize.y - pxPageEdgeDistance, widgetTextPaint);
        String percentage = "( " + (lastLineIndex + 1) * 100 / mLoader.getElementCount() + "% )";
        int tempWidth = (int) widgetTextPaint.measureText(percentage);
        canvas.drawText(percentage, screenSize.x - pxPageEdgeDistance - tempWidth, screenSize.y - pxPageEdgeDistance, widgetTextPaint);

        // TODO: draw text on average in page and line
        int heightSum = fontHeight + pxPageEdgeDistance + pxWidgetHeight;
        for(int i = 0; i < lineInfoList.size(); i ++) {
            LineInfo li = lineInfoList.get(i);
            if( i != 0 ) {
                if(li.text.length() > 2 && li.text.substring(0, 2).equals("　　")) {
                    heightSum += pxParagraphDistance;
                }
                else {
                    heightSum += pxLineDistance;
                }
            }

            Log.e("MewX", "draw: " + li.text);
            canvas.drawText(li.type == WenkuReaderLoader.ElementType.TEXT ? li.text : "（！请先用旧引擎浏览）图片" + li.text.substring(20, li.text.length()), (float) (pxPageEdgeDistance + pxParagraphEdgeDistance), (float) heightSum, textPaint);
            heightSum += fontHeight;
        }
    }

    public int getFirstLineIndex() {
        return firstLineIndex;
    }

    public int getFirstWordIndex() {
        return firstWordIndex;
    }

    public int getLastLineIndex() {
        return lastLineIndex;
    }

    public int getLastWordIndex() {
        return lastWordIndex;
    }

    private class AsyncLoadImage extends AsyncTask<String, Integer, Wenku8Error.ErrorCode> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(String... params) {
            return null;
        }
    }
}

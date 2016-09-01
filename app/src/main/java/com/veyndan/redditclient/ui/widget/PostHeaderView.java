package com.veyndan.redditclient.ui.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.LineHeightSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.widget.TextView;

import com.binaryfork.spanny.Spanny;
import com.veyndan.redditclient.post.Flair;
import com.veyndan.redditclient.R;

import java.util.List;

import butterknife.BindDimen;
import butterknife.ButterKnife;

public class PostHeaderView extends TextView {

    @BindDimen(R.dimen.post_title_subtitle_spacing) int titleSubtitleSpacing;
    @BindDimen(R.dimen.post_subtitle_flair_spacing) int subtitleFlairSpacing;

    private final Context context;

    private final TextAppearanceSpan titleTextAppearanceSpan;

    private final TextAppearanceSpan subtitleTextAppearanceSpan;

    private final TextAppearanceSpan flairTextAppearanceSpan;
    private final StyleSpan flairStyleSpan = new StyleSpan(Typeface.BOLD);

    public PostHeaderView(final Context context) {
        this(context, null);
    }

    public PostHeaderView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PostHeaderView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ButterKnife.bind(this);

        this.context = context;

        titleTextAppearanceSpan = new TextAppearanceSpan(context, R.style.PostTitleTextAppearance);
        subtitleTextAppearanceSpan = new TextAppearanceSpan(context, R.style.PostSubtitleTextAppearance);
        flairTextAppearanceSpan = new TextAppearanceSpan(context, R.style.PostFlairTextAppearance);
    }

    public void setHeader(final String title, final String subtitle,
                          @NonNull final List<Flair> flairs) {
        final LineHeightSpan subtitleLineHeightSpan = new LineHeightSpan.WithDensity() {
            @Override
            public void chooseHeight(final CharSequence text, final int start, final int end,
                                     final int spanstartv, final int v,
                                     final Paint.FontMetricsInt fm, final TextPaint paint) {
                fm.ascent -= titleSubtitleSpacing;
                fm.top -= titleSubtitleSpacing;

                if (!flairs.isEmpty()) {
                    fm.descent += subtitleFlairSpacing;
                    fm.bottom += subtitleFlairSpacing;
                }
            }

            @Override
            public void chooseHeight(final CharSequence text, final int start, final int end,
                                     final int spanstartv, final int v,
                                     final Paint.FontMetricsInt fm) {
                chooseHeight(text, start, end, spanstartv, v, fm, null);
            }
        };

        final Spanny spanny = new Spanny(title, titleTextAppearanceSpan)
                .append("\n")
                .append(subtitle, subtitleTextAppearanceSpan, subtitleLineHeightSpan);

        if (!flairs.isEmpty()) {
            spanny.append("\n");

            final Spanny flairsSpanny = new Spanny();

            String divider = "";
            for (final Flair flair : flairs) {
                flairsSpanny.append(divider);
                if (divider.isEmpty()) {
                    divider = "   "; // TODO Replace with margin left and right of 4dp
                }

                flairsSpanny.append(createFlairSpannable(flair));
            }

            spanny.append(flairsSpanny, new LineHeightSpan.WithDensity() {
                @Override
                public void chooseHeight(final CharSequence text, final int start, final int end,
                                         final int spanstartv, final int v,
                                         final Paint.FontMetricsInt fm, final TextPaint paint) {
                    // Reset titleSubtitleSpacing.
                    fm.ascent += titleSubtitleSpacing;
                    fm.top += titleSubtitleSpacing;

                    // Reset subtitleFlairSpacing.
                    fm.descent -= subtitleFlairSpacing;
                    fm.bottom -= subtitleFlairSpacing;
                }

                @Override
                public void chooseHeight(final CharSequence text, final int start, final int end,
                                         final int spanstartv, final int v,
                                         final Paint.FontMetricsInt fm) {
                    chooseHeight(text, start, end, spanstartv, v, fm, null);
                }
            });
        }

        setText(spanny);
    }

    private SpannableString createFlairSpannable(final Flair flair) {
        final FlairBackgroundSpan flairBackgroundSpan = new FlairBackgroundSpan(context,
                flair.getBackgroundColor(), flair.getIcon());

        final StringBuilder tagBuilder = new StringBuilder(2);

        if (flair.getText() != null) {
            tagBuilder.append(flair.getText());
        }

        return Spanny.spanText(tagBuilder, flairTextAppearanceSpan, flairStyleSpan, flairBackgroundSpan);
    }

    private static final class FlairBackgroundSpan extends ReplacementSpan {

        @ColorInt private final int backgroundColor;
        @ColorInt private final int textColor;
        @Nullable private final Drawable icon;

        private final int cornerRadius;
        private final int paddingHorizontal;
        private final int paddingVertical;
        private final int paddingDrawable;

        private FlairBackgroundSpan(final Context context, @ColorInt final int backgroundColor,
                            @Nullable final Drawable icon) {
            this.backgroundColor = backgroundColor;
            textColor = ContextCompat.getColor(context, android.R.color.white);
            this.icon = icon;

            final Resources resources = context.getResources();
            cornerRadius = resources.getDimensionPixelSize(R.dimen.post_flair_corner_radius);
            paddingHorizontal = resources.getDimensionPixelSize(R.dimen.post_flair_padding_horizontal);
            paddingVertical = resources.getDimensionPixelSize(R.dimen.post_flair_padding_vertical);
            paddingDrawable = resources.getDimensionPixelSize(R.dimen.post_flair_padding_drawable);
        }

        @Override
        public void draw(@NonNull final Canvas canvas, final CharSequence text, final int start,
                         final int end, final float x, final int top, final int y, final int bottom,
                         @NonNull final Paint paint) {
            int drawablePadding = 0;
            if (icon != null) {
                drawablePadding += icon.getIntrinsicWidth() + paddingDrawable;
            }

            final RectF rect = new RectF(x, top, x + paint.measureText(text, start, end) + paddingHorizontal * 2 + drawablePadding, bottom);
            paint.setColor(backgroundColor);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

            final TextPaint textPaint = new TextPaint(paint);
            textPaint.setColor(textColor);
            textPaint.setTextAlign(Paint.Align.CENTER);

            final float textHeight = textPaint.descent() - textPaint.ascent();
            final float textOffset = (textHeight / 2) - textPaint.descent();

            if (icon != null) {
                final Rect paddedRect = new Rect(
                        (int) rect.left + paddingHorizontal,
                        (int) rect.top + paddingVertical,
                        (int) rect.right - paddingHorizontal,
                        (int) rect.bottom - paddingVertical);

                icon.setBounds(
                        paddedRect.left,
                        (int) (paddedRect.top + textPaint.descent()),
                        paddedRect.left + icon.getIntrinsicWidth(),
                        (int) (paddedRect.top + textPaint.descent() + icon.getIntrinsicHeight()));

                icon.draw(canvas);
            }

            canvas.drawText(text, start, end, rect.centerX() + drawablePadding / 2,
                    rect.centerY() + textOffset, textPaint);
        }

        @Override
        public int getSize(@NonNull final Paint paint, final CharSequence text, final int start,
                           final int end, final Paint.FontMetricsInt fm) {
            if (fm != null) {
                fm.descent += paddingVertical * 2;
                fm.bottom += paddingVertical * 2;
            }
            return Math.round(paint.measureText(text, start, end) + paddingHorizontal * 2);
        }
    }
}
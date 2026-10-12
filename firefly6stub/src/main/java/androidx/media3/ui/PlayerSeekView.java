package androidx.media3.ui;

import android.content.Context;
import android.graphics.Rect;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaChapter;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;

import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

/**
 * 上游定制 media3 私有类 PlayerSeekView 的还原实现。
 *
 * <p>由官方 5.6.1 APK 反编译还原（类名与 public API 经 R8 keep 保留原名）：
 * 显示当前时间 / 总时长 / 当前章节名，提供章节气泡（拖动时显示章节标题），
 * 并将章节数据绑定到 {@link DefaultTimeBar}。
 *
 * <p>与上游差异（webhtv 定制版 media3 1.11.0-alpha01 无对应 API）：
 * 进度条上的章节标记绘制（DefaultTimeBar.setChapterTimes）不可用，章节信息
 * 通过时间文本与气泡呈现。
 */
public class PlayerSeekView extends FrameLayout implements Player.Listener, TimeBar.OnScrubListener {

    private final TextView positionView;
    private final TextView durationView;
    private final TextView chapterView;
    private final TextView chapterSeparator;
    private final DefaultTimeBar timeBar;
    private final StringBuilder timeTextBuilder;
    private final Formatter formatter;
    private final Runnable ticker;
    private final Timeline.Period period;
    private final Timeline.Window window;
    private final Rect bubbleFrame;
    private final Rect rootFrame;
    private final int[] timeBarLocation;
    private final int[] rootLocation;
    private final int bubbleMargin;
    private final int bubbleScreenInset;
    private final int bubbleMaxWidth;

    private long durationMs;
    private long[] adGroupTimesMs;
    private boolean[] adGroupPlayed;
    private long[] chapterTimesMs;
    @Nullable private String[] chapterLabels;
    private int chapterCount;
    private boolean isAttachedToWindow;
    private boolean listenerAdded;
    private boolean scrubbing;
    @Nullable private Player player;
    @Nullable private TextView chapterBubble;
    @Nullable private ViewGroup chapterBubbleRoot;
    @Nullable private String lastChapterTitle;
    private int lastBubbleMaxWidth;
    private int bubbleWidth;
    private int bubbleHeight;

    public PlayerSeekView(Context context) {
        this(context, null);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(androidx.media3.mpvplayer.R.layout.exo_player_seek_view, this);
        positionView = findViewById(androidx.media3.ui.R.id.exo_position);
        positionView.getClass();
        durationView = findViewById(androidx.media3.ui.R.id.exo_duration);
        durationView.getClass();
        timeBar = findViewById(androidx.media3.ui.R.id.exo_progress);
        timeBar.getClass();
        timeBar.addListener(this);
        chapterView = findViewById(androidx.media3.mpvplayer.R.id.exo_chapter);
        chapterSeparator = findViewById(androidx.media3.mpvplayer.R.id.exo_chapter_separator);
        timeTextBuilder = new StringBuilder();
        formatter = new Formatter(timeTextBuilder, Locale.getDefault());
        ticker = this::updateProgress;
        period = new Timeline.Period();
        window = new Timeline.Window();
        bubbleFrame = new Rect();
        rootFrame = new Rect();
        timeBarLocation = new int[2];
        rootLocation = new int[2];
        bubbleMargin = getResources().getDimensionPixelSize(androidx.media3.mpvplayer.R.dimen.exo_chapter_bubble_margin);
        bubbleScreenInset = getResources().getDimensionPixelSize(androidx.media3.mpvplayer.R.dimen.exo_chapter_bubble_screen_inset);
        bubbleMaxWidth = getResources().getDimensionPixelSize(androidx.media3.mpvplayer.R.dimen.exo_chapter_bubble_max_width);
        adGroupTimesMs = new long[0];
        adGroupPlayed = new boolean[0];
        chapterTimesMs = new long[0];
        reset();
    }

    /** 返回内部进度条，供外部（如 PlayerView）绑定。 */
    public TimeBar getTimeBar() {
        return timeBar;
    }

    /** 绑定/解绑播放器。 */
    public void setPlayer(@Nullable Player newPlayer) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException();
        if (newPlayer != null && newPlayer.getApplicationLooper() != Looper.getMainLooper()) {
            throw new IllegalArgumentException("Player must be accessed from the main thread");
        }
        if (this.player != newPlayer) {
            removeCallbacks(ticker);
            if (this.player != null && listenerAdded) {
                this.player.removeListener(this);
                listenerAdded = false;
            }
            this.player = newPlayer;
            if (newPlayer == null) {
                reset();
            }
            if (isAttachedToWindow) {
                if (newPlayer != null && !listenerAdded) {
                    newPlayer.addListener(this);
                    listenerAdded = true;
                }
                updateChapters();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttachedToWindow = true;
        if (player != null && !listenerAdded) {
            player.addListener(this);
            listenerAdded = true;
        }
        updateChapters();
    }

    @Override
    protected void onDetachedFromWindow() {
        isAttachedToWindow = false;
        scrubbing = false;
        hideChapterBubble();
        if (player != null && listenerAdded) {
            player.removeListener(this);
            listenerAdded = false;
        }
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) {
            hideChapterBubble();
        }
    }

    @Override
    public void onEvents(Player player, Player.Events events) {
        if (events.contains(Player.EVENT_MEDIA_CHAPTERS_CHANGED)
                || events.contains(Player.EVENT_TIMELINE_CHANGED)
                || events.contains(Player.EVENT_MEDIA_EDITIONS_CHANGED)
                || events.contains(Player.EVENT_PLAYLIST_METADATA_CHANGED)) {
            updateChapters();
        } else if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                || events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
            updateProgress();
        }
    }

    // ---------- TimeBar.OnScrubListener ----------

    @Override
    public void onScrubStart(TimeBar timeBar, long positionMs) {
        scrubbing = true;
    }

    @Override
    public void onScrubMove(TimeBar timeBar, long positionMs) {
        updatePosition(positionMs);
    }

    @Override
    public void onScrubStop(TimeBar timeBar, long positionMs, boolean canceled) {
        scrubbing = false;
        if (!canceled && player != null
                && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.seekTo(positionMs);
        }
        updateProgress();
    }

    // ---------- 内部逻辑 ----------

    /** 定时刷新进度显示。 */
    private void updateProgress() {
        removeCallbacks(ticker);
        if (!isAttachedToWindow || getVisibility() != VISIBLE || player == null) {
            return;
        }
        long position = player.getCurrentPosition();
        long buffered = player.getBufferedPosition();
        if (!scrubbing) {
            positionView.setText(getTimeString(position));
            setChapterTitle(getChapterTitle(position));
        }
        timeBar.setPosition(position);
        timeBar.setBufferedPosition(buffered);
        int state = player.getPlaybackState();
        long delayMs = 1000;
        if (player.isPlaying()) {
            float speed = player.getPlaybackParameters().speed;
            long updateDelay = Math.min(timeBar.getPreferredUpdateDelay(), 1000 - position % 1000);
            if (speed > 0) {
                delayMs = (long) (updateDelay / speed);
            }
            delayMs = Util.constrainValue(delayMs, 200, 1000);
        } else if (state != Player.STATE_ENDED && state != Player.STATE_IDLE) {
            delayMs = 1000;
        } else {
            return;
        }
        postDelayed(ticker, delayMs);
    }

    /** 更新时长与章节标记（Timeline / 章节列表变化时）。 */
    private void updateChapters() {
        if (!isAttachedToWindow || player == null) {
            return;
        }
        Timeline timeline = player.getCurrentTimeline();
        long durationMs = C.TIME_UNSET;
        int adGroupCount = 0;
        if (!timeline.isEmpty()) {
            timeline.getWindow(player.getCurrentWindowIndex(), window);
            durationMs = window.getDurationMs();
            if (durationMs != C.TIME_UNSET) {
                int firstPeriodIndex = window.firstPeriodIndex;
                int lastPeriodIndex = window.lastPeriodIndex;
                while (firstPeriodIndex <= lastPeriodIndex) {
                    timeline.getPeriod(firstPeriodIndex, period, true);
                    int adGroupCountInPeriod = period.getAdGroupCount();
                    int adGroupIndex = 0;
                    while (adGroupIndex < adGroupCountInPeriod) {
                        long adGroupTimeUs = period.getAdGroupTimeUs(adGroupIndex);
                        if (adGroupTimeUs == C.TIME_UNSET) {
                            adGroupIndex++;
                            continue;
                        }
                        long adGroupTimeMs = Util.usToMs(adGroupTimeUs + period.getPositionInWindowUs());
                        if (adGroupTimeMs >= durationMs) {
                            break;
                        }
                        if (adGroupCount == adGroupTimesMs.length) {
                            int newLength = adGroupTimesMs.length == 0 ? 1 : adGroupTimesMs.length * 2;
                            adGroupTimesMs = Arrays.copyOf(adGroupTimesMs, newLength);
                            adGroupPlayed = Arrays.copyOf(adGroupPlayed, newLength);
                        }
                        adGroupTimesMs[adGroupCount] = adGroupTimeMs;
                        adGroupPlayed[adGroupCount] = !period.hasPlayedAdGroup(adGroupIndex);
                        adGroupCount++;
                        adGroupIndex++;
                    }
                    firstPeriodIndex++;
                }
            }
        } else if (player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
            durationMs = player.getCurrentPosition();
        }
        this.durationMs = durationMs;
        timeBar.setDuration(durationMs);
        timeBar.setAdGroupTimesMs(adGroupTimesMs, adGroupPlayed, adGroupCount);
        durationView.setText(getTimeString(durationMs));
        setMediaChapters(player.getCurrentMediaChapters());
        updateProgress();
    }

    /** 从当前章节列表构建时间/标题数组（进度条章节标记 API 缺失时仅保留数据）。 */
    private void setMediaChapters(List<MediaChapter> chapters) {
        if (!chapters.isEmpty() && durationMs > 0) {
            long[] times = new long[chapters.size()];
            String[] labels = new String[chapters.size()];
            int count = 0;
            int hasLabels = 0;
            for (MediaChapter chapter : chapters) {
                long timeMs = Util.usToMs(chapter.timeUs);
                if (timeMs != C.TIME_UNSET && timeMs >= 0 && timeMs < durationMs) {
                    times[count] = timeMs;
                    String label = chapter.label == null ? "" : chapter.label;
                    labels[count] = label;
                    hasLabels |= label.isEmpty() ? 0 : 1;
                    count++;
                }
            }
            chapterCount = count;
            chapterTimesMs = count == 0 ? null : Arrays.copyOf(times, count);
            chapterLabels = count != 0 && hasLabels != 0 ? Arrays.copyOf(labels, count) : null;
            // webhtv media3 无 setChapterTimes(long[], int) API，进度条章节标记省略
        } else {
            resetChapters();
        }
    }

    /** 清空章节数据与显示。 */
    private void resetChapters() {
        chapterCount = 0;
        chapterTimesMs = null;
        chapterLabels = null;
        hideChapterBubble();
        setChapterTitle(null);
    }

    /** 查找 positionMs 所在的章节标题。 */
    @Nullable
    private String getChapterTitle(long positionMs) {
        if (positionMs == C.TIME_UNSET || chapterCount == 0 || chapterTimesMs == null) {
            return null;
        }
        long latestTime = Long.MIN_VALUE;
        int latestIndex = -1;
        for (int i = 0; i < chapterCount; i++) {
            long time = chapterTimesMs[i];
            if (time != C.TIME_UNSET && time <= positionMs && time >= latestTime) {
                latestIndex = i;
                latestTime = time;
            }
        }
        if (latestIndex != -1 && chapterLabels != null) {
            String label = chapterLabels[latestIndex];
            if (!TextUtils.isEmpty(label)) {
                return label;
            }
        }
        return null;
    }

    /** 显示/隐藏章节名文本。 */
    private void setChapterTitle(@Nullable String title) {
        if (chapterView != null) {
            if (title != null) {
                chapterView.setText(title);
                chapterView.setVisibility(VISIBLE);
                setChapterSeparatorVisible(true);
            } else {
                chapterView.setText(null);
                chapterView.setVisibility(GONE);
                setChapterSeparatorVisible(false);
            }
        } else {
            setChapterSeparatorVisible(false);
        }
    }

    private void setChapterSeparatorVisible(boolean visible) {
        if (chapterSeparator != null) {
            chapterSeparator.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    /** 更新位置显示与章节气泡（拖动/seek 时）。 */
    private void updatePosition(long positionMs) {
        positionView.setText(getTimeString(positionMs));
        String title = getChapterTitle(positionMs);
        setChapterTitle(title);
        if (title != null && isAttachedToWindow && isShown()) {
            updateChapterBubble(title, positionMs);
        } else {
            hideChapterBubble();
        }
    }

    /** 章节气泡：在进度条上方显示当前章节标题。 */
    private void updateChapterBubble(String title, long positionMs) {
        ViewGroup root = getChapterBubbleRoot();
        TextView bubble = getChapterBubbleView();
        Rect frame = getChapterBubbleHorizontalFrame();
        int horizontalInset = bubbleScreenInset;
        int maxWidth = Math.min(bubbleMaxWidth, Math.max(1, frame.width() - horizontalInset * 2));
        if (!title.equals(lastChapterTitle) || lastBubbleMaxWidth != maxWidth) {
            bubble.setMaxWidth(maxWidth);
            bubble.setText(title);
            bubble.measure(
                    View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            lastChapterTitle = title;
            lastBubbleMaxWidth = maxWidth;
            bubbleWidth = bubble.getMeasuredWidth();
            bubbleHeight = bubble.getMeasuredHeight();
        }
        timeBar.getLocationOnScreen(timeBarLocation);
        root.getLocationOnScreen(rootLocation);
        int barLeft = timeBar.getLeft();
        int barWidth = timeBar.getWidth();
        int left;
        if (barWidth <= 0) {
            left = barLeft;
        } else if (durationMs > 0) {
            // webhtv DefaultTimeBar 无 getBarFrame()，用控件整体定位近似对齐
            left = barLeft + (int) (barWidth * Util.constrainValue(positionMs, 0, durationMs) / durationMs);
        } else {
            left = barLeft;
        }
        int bubbleLeft = Util.constrainValue(
                timeBarLocation[0] + left - bubbleWidth / 2,
                frame.left + horizontalInset,
                Math.max(frame.left + horizontalInset, frame.right - horizontalInset - bubbleWidth))
                - rootLocation[0];
        // webhtv DefaultTimeBar 无 getProgressBarTopInView()，气泡直接位于进度条上方
        int bubbleTop = timeBarLocation[1] - bubbleHeight - bubbleMargin - rootLocation[1];
        if (chapterBubbleRoot != root) {
            hideChapterBubble();
            chapterBubbleRoot = root;
            root.getOverlay().add(bubble);
        }
        bubble.layout(bubbleLeft, bubbleTop, bubbleLeft + bubbleWidth, bubbleTop + bubbleHeight);
    }

    private Rect getChapterBubbleHorizontalFrame() {
        Rect frame = rootFrame;
        getGlobalVisibleRect(frame);
        return frame;
    }

    private ViewGroup getChapterBubbleRoot() {
        View root = getRootView();
        if (root instanceof ViewGroup) {
            return (ViewGroup) root;
        }
        return this;
    }

    private TextView getChapterBubbleView() {
        if (chapterBubble == null) {
            chapterBubble = (TextView) LayoutInflater.from(getContext())
                    .inflate(androidx.media3.mpvplayer.R.layout.exo_chapter_bubble, this, false);
        }
        return chapterBubble;
    }

    private void hideChapterBubble() {
        if (chapterBubbleRoot != null) {
            chapterBubbleRoot.getOverlay().remove(chapterBubble);
            chapterBubbleRoot = null;
        }
    }

    private String getTimeString(long timeMs) {
        return Util.getStringForTime(timeTextBuilder, formatter, timeMs);
    }

    /** 重置全部显示。 */
    private void reset() {
        positionView.setText(getTimeString(0));
        durationView.setText(getTimeString(0));
        timeBar.setPosition(0);
        durationMs = 0;
        timeBar.setDuration(0);
        timeBar.setBufferedPosition(0);
        timeBar.setAdGroupTimesMs(new long[0], new boolean[0], 0);
        resetChapters();
    }
}

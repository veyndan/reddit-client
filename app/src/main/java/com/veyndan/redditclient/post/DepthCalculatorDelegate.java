package com.veyndan.redditclient.post;

import android.support.annotation.IntRange;

public interface DepthCalculatorDelegate {

    @IntRange(from = 0)
    int depthForPosition(@IntRange(from = 0) int position);
}

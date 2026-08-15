package com.middleproject.reminder.domain;

/**
 * How far before the departure date weather should be considered: the departure day
 * itself or the previous day as well.
 */
public enum DepartureTiming {
    SAME_DAY,
    PREVIOUS_DAY
}

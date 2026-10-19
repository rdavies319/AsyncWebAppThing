package com.rdavies.api.round;

/**
 * Published by the round engine when a round opens, i.e. right after its roll. Carries the round as
 * clients see it, so listeners don't have to query for it after the tick commits.
 */
public record RoundOpened(RoundView view) {}

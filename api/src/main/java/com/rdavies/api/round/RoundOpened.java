package com.rdavies.api.round;

/** Published by the round engine when a round opens, i.e. right after its roll. */
public record RoundOpened(Long roundId) {}

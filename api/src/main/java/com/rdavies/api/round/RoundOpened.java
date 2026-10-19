package com.rdavies.api.round;

/** Published by the round engine when a new round opens, i.e. right after a roll (or a voided round). */
public record RoundOpened(Long roundId) {}

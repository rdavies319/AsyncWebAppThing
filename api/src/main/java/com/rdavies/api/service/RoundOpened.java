package com.rdavies.api.service;

/** Published by the round engine when a new round opens, i.e. right after a roll (or a voided round). */
public record RoundOpened(Long roundId) {}

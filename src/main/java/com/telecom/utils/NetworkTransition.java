package com.telecom.utils;

/**
 *  Network transition
 */
public class NetworkTransition {
    private int timestamp; // seconds
    private String fromState;
    private String fromRAT;
    private String toState;
    private String toRAT;
    
    public NetworkTransition(int timestamp, String fromState, String fromRAT, 
                           String toState, String toRAT) {
        this.timestamp = timestamp;
        this.fromState = fromState != null ? fromState : "UNKNOWN";
        this.fromRAT = fromRAT != null ? fromRAT : "UNKNOWN";
        this.toState = toState;
        this.toRAT = toRAT;
    }
    
    // Getters
    public int getTimestamp() { return timestamp; }
    public String getFromState() { return fromState; }
    public String getFromRAT() { return fromRAT; }
    public String getToState() { return toState; }
    public String getToRAT() { return toRAT; }
}
package com.telecom.utils;

import java.time.Instant;
import java.util.List;

/**
 * ✅ Network registration event
 */
public class NetworkRegistrationEvent {
    private boolean registered;
    private Instant registrationTime;
    private String finalNetworkState;
    private String finalRAT;
    private List<NetworkTransition> transitions;
    
    // Getters and Setters
    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }
    
    public Instant getRegistrationTime() { return registrationTime; }
    public void setRegistrationTime(Instant registrationTime) { this.registrationTime = registrationTime; }
    
    public String getFinalNetworkState() { return finalNetworkState; }
    public void setFinalNetworkState(String finalNetworkState) { this.finalNetworkState = finalNetworkState; }
    
    public String getFinalRAT() { return finalRAT; }
    public void setFinalRAT(String finalRAT) { this.finalRAT = finalRAT; }
    
    public List<NetworkTransition> getTransitions() { return transitions; }
    public void setTransitions(List<NetworkTransition> transitions) { this.transitions = transitions; }
}
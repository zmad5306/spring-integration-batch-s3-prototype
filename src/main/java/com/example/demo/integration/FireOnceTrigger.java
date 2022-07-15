package com.example.demo.integration;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.util.Date;

public class FireOnceTrigger implements Trigger {

    boolean done;

    public Date nextExecutionTime(TriggerContext triggerContext) {
        if (done) {
            return null;
        }
        done = true;
        return new Date();
    }

    public void reset() {
        done = false;
    }
}
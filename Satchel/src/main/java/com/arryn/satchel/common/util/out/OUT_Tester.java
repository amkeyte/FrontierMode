package com.arryn.satchel.common.util.out;

import com.arryn.satchel.common.util.schedule.SchedulerClock;

import java.util.ArrayList;

class OUT_Tester {



    private final Tracer localTracer = new Tracer("tracer2");

    /**
     * these are some of the stupid things you can do with the Tracer/
     */
    private void joker(){
        localTracer.log("I like Pie");

        var pieEaters = new ArrayList<String>();
        //TRACE.log(pieEaters);
        //OUT.TRACE().logEach(pieEaters);
        //TRACE.chat(pieEaters); future
        localTracer.disable();
        OUT.TRACE().log("print this.");
        localTracer.log("don't print this");
        localTracer.enable();
        OUT.TRACE().log("I don't like Pie");
        OUT.TRACE("myTracer");

        //register a new caller to the OUT main repository, using a dummy clock for example
        //registered callers are throttled and therefore appropriate for render/onJigTick threads without
        //spamming the log.
        OUT.TRACE().registerCaller(this,"joker",new SchedulerClock.NullClock(),50L);
        //use the registered tracer
        OUT.TRACE().log(this,"joker","I do bad things!!");
        //get the deterministic ID from the caller (
        // Also could just create a string e.g. "myClassyJokerId" and supply that as source
        String jokerCallerId = OUT.TRACE().getCaller(this,"joker").sourceId;

        //put the local tracer instance on the global registry.
        OUT.registerTracer(localTracer);
        //copy the local caller into the local tracer
        OUT.TRACE("tracer2").registerCaller(OUT.TRACE().getCaller(jokerCallerId));

        // remove the local tracer from the global registry.
        if(!OUT.TRACE().removeCaller("tracer2")){
            OUT.error("Houston, we lost tracer 2");
        }
        //verify that the new tracer now contains the joker caller.
        OUT.info("joker's caller ID is: " + localTracer.getCaller(jokerCallerId).sourceId);

    }
}

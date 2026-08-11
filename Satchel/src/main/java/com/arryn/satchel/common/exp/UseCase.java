package com.arryn.satchel.common.exp;

public class UseCase {
    public void scratch() {
        HomeConfig hcfg = new HomeConfig("a key");

        hcfg
                .itemA("has been set")
                .itemC("I cared, so I'm changing it");

        hcfg.categoryA()
                .paramC("jjajajaj");



    var retrievedLater = hcfg.categoryA().paramB();

    }
}

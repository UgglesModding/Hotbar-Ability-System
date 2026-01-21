package com.example.exampleplugin;

public class AbilityData {
    public String ID;
    public String Name;
    public String Icon;
    public String ItemAsset;
    public String Model;

    // Must be named EXACTLY "Interactions" to match JSON key "Interactions"
    public Interactions Interactions;

    public static class Interactions {
        // Must be named EXACTLY "Use" to match JSON key "Use"
        public String Use;

        // Optional extras for later, harmless to keep
        public String SwapTo;
        public String Primary;
        public String Secondary;
    }
}

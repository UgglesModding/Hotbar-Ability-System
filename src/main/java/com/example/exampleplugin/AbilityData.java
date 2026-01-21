package com.example.exampleplugin;

public class AbilityData {
    public String ID;
    public String Name;
    public String Icon;
    public String ItemAsset;
    public String Model;

    public Interactions Interactions;

    public static class Interactions {
        public String Use;
        public String SwapTo;     // keep for compatibility, even if unused now
        public String Primary;
        public String Secondary;
    }
}

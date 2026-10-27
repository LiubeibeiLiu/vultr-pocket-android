package app.vultrpocket.mobile;

final class ReadLifecycle {
    private volatile boolean foreground;
    private volatile long generation;
    synchronized void foreground(boolean value){if(!value)generation++;foreground=value;}
    synchronized void cancel(){generation++;}
    boolean foreground(){return foreground;}
    synchronized long begin(){return foreground?generation:-1;}
    boolean valid(long ticket){return ticket>=0&&foreground&&ticket==generation;}
}

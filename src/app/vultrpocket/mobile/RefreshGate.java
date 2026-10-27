package app.vultrpocket.mobile;

/** Persists successful-refresh time; failed requests never move the deadline. */
final class RefreshGate {
    interface Store { Record load();void save(Record record) throws Exception; }
    static final class Record {
        final String binding;final long succeededAt,wall,elapsed,boot;
        Record(String binding,long succeededAt,long wall,long elapsed,long boot){this.binding=binding;this.succeededAt=succeededAt;this.wall=wall;this.elapsed=elapsed;this.boot=boot;}
    }
    private final Store store;
    RefreshGate(Store store){this.store=store;}
    synchronized long remaining(String binding,long wall,long elapsed,long boot) throws Exception {
        Record record=store.load();
        if(record==null||!record.binding.equals(binding)||record.wall<=0)return 0;
        boolean sameBoot=boot>=0&&record.boot>=0?boot==record.boot:Math.abs((wall-elapsed)-(record.wall-record.elapsed))<=300000;
        long age=sameBoot&&elapsed>=record.elapsed?elapsed-record.elapsed:wall-record.wall;
        if(age<0){store.save(new Record(binding,record.succeededAt,wall,elapsed,boot));return RefreshPolicy.MIN_INTERVAL_MS;}
        return Math.max(0,RefreshPolicy.MIN_INTERVAL_MS-age);
    }
    synchronized boolean allowed(String binding,long wall,long elapsed,long boot,boolean manual) throws Exception {
        return manual||remaining(binding,wall,elapsed,boot)==0;
    }
    synchronized void succeeded(String binding,long wall,long elapsed,long boot) throws Exception {
        store.save(new Record(binding,wall,wall,elapsed,boot));
    }
    synchronized long lastSuccessAt(String binding){Record record=store.load();return record!=null&&record.binding.equals(binding)?record.succeededAt:0;}
}


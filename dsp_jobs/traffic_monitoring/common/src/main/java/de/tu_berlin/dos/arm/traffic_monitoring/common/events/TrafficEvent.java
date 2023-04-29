package de.tu_berlin.dos.arm.traffic_monitoring.common.events;

public class TrafficEvent {

    private String lp;
    private Point pt;
    //@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone="UTC")
    //private Date ts;
    private long ts;

    public TrafficEvent() { }

    //public TrafficEvent(String lp, Point pt, Date ts) {
    public TrafficEvent(String lp, Point pt, long ts) {
        this.lp = lp;
        this.pt = pt;
        this.ts = ts;
    }

    public void setLp(String lp) {
        this.lp = lp;
    }

    public void setPt(Point pt) {
        this.pt = pt;
    }

    /*public void setTs(Date ts) {
        this.ts = ts;
    }*/


    public String getLp() {
        return lp;
    }

    public Point getPt() {
        return pt;
    }

    /*public Date getTs() {
        return ts;
    }*/

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    @Override
    public String toString() {
        return "{lp:" + lp + ",pt:" + pt + ",ts:" + ts + '}';
    }
}

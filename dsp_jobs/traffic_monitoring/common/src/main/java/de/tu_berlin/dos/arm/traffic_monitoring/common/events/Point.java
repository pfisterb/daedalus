package de.tu_berlin.dos.arm.traffic_monitoring.common.events;

import java.io.Serializable;
import java.util.Objects;

public class Point implements Serializable {

    public float lt;
    public float lg;

    public Point() { }

    public Point(float lt, float lg) {
        this.lt = lt;
        this.lg = lg;
    }

    public float getLt() {
        return lt;
    }

    public void setLt(float lt) {
        this.lt = lt;
    }

    public float getLg() {
        return lg;
    }

    public void setLg(float lg) {
        this.lg = lg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point point = (Point) o;
        return Double.compare(point.lt, lt) == 0 &&
               Double.compare(point.lg, lg) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lt, lg);
    }

    @Override
    public String toString() {
        return "{lt:" + lt + ",ln:" + lg + "}";
    }
}

package dev.edgeflow;

/** Main-thread ownership; an old Activity may stop after its successor has resumed. */
final class HandlePreviewOwner {
    static final Object TEMPORARY=new Object();
    private Object owner;
    void replace(Object next){if(next==null)throw new IllegalArgumentException("Missing preview owner");owner=next;}
    boolean release(Object expected){if(expected==null||owner!=expected)return false;owner=null;return true;}
    void clear(){owner=null;}
}

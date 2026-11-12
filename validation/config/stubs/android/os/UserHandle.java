package android.os;

public final class UserHandle {
    private final int id;
    public UserHandle(int id){this.id=id;}
    @Override public boolean equals(Object value){return value instanceof UserHandle other&&id==other.id;}
    @Override public int hashCode(){return id;}
}

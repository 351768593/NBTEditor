package nbt.record;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import nbt.write.ByteWriter;

/**
 * A nbt list contains arbitrary many elements of the same record type.
 * 
 * @author Joschi <josua.krause@googlemail.com>
 * @param <T> The content type.
 */
public class NBTList<T extends NBTRecord> extends NBTRecord implements
    Iterable<T> {

  private final NBTType type;

  private NBTRecord[] records;

  /**
   * Creates a new list record.
   * 
   * @param name The name of the record.
   * @param type The type of the elements of the list.
   * @param records The list of elements.
   */
  public NBTList(final String name, final NBTType type,
      final T[] records) {
    super(NBTType.LIST, name);
    this.type = type;
    setArray(records);
  }

  /**
   * Getter.
   * 
   * @return The length of the list.
   */
  public int getLength() {
    return records.length;
  }

  /**
   * Getter.
   * 
   * @param pos The index.
   * @return The element at the given index.
   */
  @SuppressWarnings("unchecked")
  public T getAt(final int pos) {
    return (T) records[pos];
  }

  /**
   * Setter.
   * 
   * @param pos The index.
   * @param rec The new value for the index. The value must not be
   *          <code>null</code> and must have the same type as all other
   *          elements in the list.
   */
  public void setAt(final int pos, final T rec) {
    if(rec.getName() != null) throw new IllegalArgumentException(
        "list items must not be named: "
            + rec.getName());
    if(rec.getType() != type) throw new IllegalArgumentException(
        "item type must be consistent: "
            + type + " expected got " + rec.getType());
    records[pos] = rec;
    change();
  }

  /**
   * Getter.
   * 
   * @param r The record.
   * @return The index of the given record (lookup by name).
   */
  public int indexOf(final NBTRecord r) {
    final String name = r.getName();
    for(int i = 0; i < records.length; ++i) {
      final String other = records[i].getName();
      if(other == name || (name != null && name.equals(other))) return i;
    }
    return -1;
  }

  /**
   * Setter.
   * 
   * @param arr Sets the new array.
   */
  public void setArray(final T[] arr) {
    records = new NBTRecord[arr.length];
    for(int i = 0; i < arr.length; ++i) {
      setAt(i, arr[i]);
    }
    change();
  }

  @Override
  public boolean hasChanged() {
    if(super.hasChanged()) return true;
    for(final NBTRecord r : records) {
      if(r.hasChanged()) return true;
    }
    return false;
  }

  @Override
  public void resetChange() {
    super.resetChange();
    for(final NBTRecord r : records) {
      r.resetChange();
    }
  }

  @Override
  public String getTypeInfo() {
    return super.getTypeInfo() + " " + type;
  }

  @Override
  public boolean hasSize() {
    return true;
  }

  @Override
  public int size() {
    return getLength();
  }

  @Override
  public void writePayload(final ByteWriter out) throws IOException {
    out.write(type.byteValue);
    out.write(records.length);
    for(final NBTRecord r : records) {
      r.writePayload(out);
    }
  }

  @Override
  public String getPayloadString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(records.length);
    sb.append(" entries\n{\n");
    for(final NBTRecord r : records) {
      sb.append(r.getPayloadString());
      sb.append("\n");
    }
    sb.append("}");
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Iterator<T> iterator() {
    return Arrays.asList((T[]) records).iterator();
  }

}

/**
 * Represents a document in the document store.
 * Documents are immutable after creation.
 * 
 * PROJECT 2 UPDATE:
 * Added DocStatus to support logical deletion.
 * Documents are never physically removed from the circular buffer,
 * but can be marked as DELETED.
 */
public class Document {
    
    /**
     * Status of a document in the store.
     */
    public enum DocStatus {
        ACTIVE,   // Document is active and searchable
        DELETED   // Document has been logically deleted
    }
    
    private final String docId;
    private final String text;
    private final long timestamp;
    private DocStatus status;  // Mutable - can change from ACTIVE to DELETED

    /**
     * Creates a new document with ACTIVE status.
     *
     * @param docId     unique identifier for this document
     * @param text      the document content
     * @param timestamp creation time in milliseconds since epoch
     */
    public Document(String docId, String text, long timestamp) {
        if (docId == null || text == null) {
            throw new IllegalArgumentException("docId and text cannot be null");
        }
        this.docId = docId;
        this.text = text;
        this.timestamp = timestamp;
        this.status = DocStatus.ACTIVE;  // All documents start as ACTIVE
    }

    /**
     * Convenience constructor that uses current time as timestamp.
     *
     * @param docId unique identifier for this document
     * @param text  the document content
     */
    public Document(String docId, String text) {
        this(docId, text, System.currentTimeMillis());
    }

    public String getDocId() {
        return docId;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }
    
    public DocStatus getStatus() {
        return status;
    }
    
    /**
     * Marks this document as deleted.
     * This is used for logical deletion - the document remains in storage
     * but is marked as inactive.
     */
    public void markAsDeleted() {
        this.status = DocStatus.DELETED;
    }
    
    /**
     * Returns true if this document is active (not deleted).
     *
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return status == DocStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return "Document{" +
                "docId='" + docId + '\'' +
                ", text='" + text + '\'' +
                ", timestamp=" + timestamp +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return docId.equals(document.docId);
    }

    @Override
    public int hashCode() {
        return docId.hashCode();
    }
}

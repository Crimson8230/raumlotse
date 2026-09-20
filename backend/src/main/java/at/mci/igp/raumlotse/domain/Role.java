package at.mci.igp.raumlotse.domain;
public enum Role {
    ADMIN("Admin"), UNIVERSITY_STAFF("University Staff"), STUDENT("Student"), LECTURER("Lecturer"), VIEWER("Viewer");
    private final String label;
    Role(String label) { this.label = label; }
    public String getLabel() { return label; }
}


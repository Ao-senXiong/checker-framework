public class EISOPIssue553 {
    static Object sfield = "";
    Object field = "";

    public static void main(String[] args) {
        EISOPIssue553 x = null;
        Object o = x.sfield;
        o = x.field;
    }
}

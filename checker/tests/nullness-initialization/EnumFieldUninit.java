enum EnumFieldUninit {
    DUMMY;

    // :: error: (assignment.type.incompatible)
    public static String s = null;

    // :: error: (initialization.static.field.uninitialized)
    public static String u;

    static String[] arrayInit = new String[] {};

    static String[] arrayInitInBlock;

    static {
        arrayInitInBlock = new String[] {};
    }

    // :: error: (assignment.type.incompatible)
    static String[] arrayInitToNull = null;

    // :: error: (initialization.static.field.uninitialized)
    static String[] arrayUninit;
}

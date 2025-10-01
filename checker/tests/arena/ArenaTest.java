import org.checkerframework.checker.arena.qual.Arena;
import org.checkerframework.checker.arena.qual.ClassArena;

@ClassArena("p")
class Test {
    // More validation need to do, need to check whether "#1" is actuall arena type
    // TODO METHOD AND CLASS REGION PARAMTER
    // TODO DSL to convert arena based implementation
    void allSubtypingRelationships(@Arena("#1") Object x, @Arena("#2") Object y) {
        // :: error: (assignment.type.incompatible)
        x = y;
    }

    // @ArenaParameter("p") what would be the default?
    class InnerRegion {
        Arena r;
        @Arena("r") Object x;
        // :: error: (assignment.type.incompatible) default object creation is at root arena
        @Arena("r") Object y = new Object();

        void method(@Arena("#1") Object r) {
            // :: error: (assignment.type.incompatible) this.r != r
            r = this.x;
        }
    }
}

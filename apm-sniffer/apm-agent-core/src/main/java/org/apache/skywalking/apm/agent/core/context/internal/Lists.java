package org.apache.skywalking.apm.agent.core.context.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Lists {

    public static List<Object> ensureMutable(List<Object> list) {
        if (list instanceof ArrayList) {
            return list;
        }
        int size = list.size();
        ArrayList<Object> mutable = new ArrayList<>(size);
        mutable.addAll(list);
        return mutable;
    }

    public static List<Object> ensureImmutable(List<Object> extra) {
        if (isImmutable(extra)) {
            return extra;
        }
        // Faster to make a copy than check the type to see if it is already a singleton list
        if (extra.size() == 1) {
            return Collections.singletonList(extra.get(0));
        }
        return Collections.unmodifiableList(new ArrayList<>(extra));
    }

    private static boolean isImmutable(List<Object> extra) {
        if (extra == Collections.EMPTY_LIST) {
            return true;
        }
        // avoid copying datastructure by trusting certain names.
        String simpleName = extra.getClass().getSimpleName();
        return simpleName.equals("SingletonList")
            || simpleName.startsWith("Unmodifiable")
            || simpleName.contains("Immutable");
    }

    public static List<Object> concatImmutableLists(List<Object> left, List<Object> right) {
        int leftSize = left.size();
        if (leftSize == 0) {
            return right;
        }
        int rightSize = right.size();
        if (rightSize == 0) {
            return left;
        }

        // now we know we have to concat
        ArrayList<Object> mutable = new ArrayList<>(left);
        mutable.addAll(right);
        return Collections.unmodifiableList(mutable);
    }

    Lists() {
    }

}

package org.apache.skywalking.apm.agent.core.context.internal;

import static org.apache.skywalking.apm.agent.core.context.internal.Lists.ensureMutable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.propagation.TraceContext;

public abstract class ExtraFactory<E> {

    public abstract Class<E> type();

    protected abstract E create();

    protected abstract E create(E parent);

    protected abstract E createExtraAndClaim(long traceId, long spanId);

    protected abstract E createExtraAndClaim(E existing, long traceId, long spanId);

    protected abstract boolean tryToClaim(E existing, long traceId, long spanId);

    protected abstract void consolidate(E existing, E consolidated);

    @SuppressWarnings("unchecked")
    public final TraceContext decorate(TraceContext context) {
        long traceId = context.traceId(), spanId = context.spanId();
        Class<E> type = type();

        List<Object> extra = context.extra();
        int extraSize = extra.size();
        if (extraSize == 0) {
            extra = (List<Object>) Collections.singletonList(createExtraAndClaim(traceId, spanId));
            return contextWithExtra(context, extra);
        }

        Object first = extra.get(0);
        E consolidated = null;

        // if the first item is a fields object, try to claim or copy its fields
        if (type.isInstance(first)) {
            E existing = (E) first;
            if (tryToClaim(existing, traceId, spanId)) {
                consolidated = existing;
            } else { // otherwise we need to consolidate the fields
                consolidated = createExtraAndClaim(existing, traceId, spanId);
            }
        }

        // If we had only one extra, there are a few options:
        // * we claimed an existing fields object successfully
        // * we copied existing fields into a new fields object claimed by this ID
        // * the existing extra was not a fields object, so we need to make a new list
        if (extraSize == 1) {
            if (consolidated != null) {
                if (consolidated == first) {
                    return context;
                }
                // otherwise we copied the fields of an existing object
                return contextWithExtra(context,
                    (List<Object>) Collections.singletonList(consolidated));
            }
            // we need to make new list to hold the unrelated extra element and our fields
            extra = new ArrayList<>(2);
            extra.add(first);
            extra.add(createExtraAndClaim(traceId, spanId));
            return contextWithExtra(context, Collections.unmodifiableList(extra));
        }

        // If we get here, we have at least one extra, but don't yet know if we need to create
        // a new list. For example, if there is an unassociated fields object we may be able to
        // avoid creating a new list.
        for (int i = 1; i < extraSize; i++) {
            Object next = extra.get(i);
            if (!type.isInstance(next)) {
                continue;
            }
            E existing = (E) next;
            if (consolidated == null) {
                if (tryToClaim(existing, traceId, spanId)) {
                    consolidated = existing;
                    continue;
                }
                consolidated = createExtraAndClaim(existing, traceId, spanId);
                extra = ensureMutable(extra);
                extra.set(i, consolidated);
            } else {
                consolidate(existing, consolidated);
                extra = ensureMutable(extra);
                extra.remove(i); // drop the previous fields item as we consolidated it
                extraSize--;
                i--;
            }
        }
        if (consolidated == null) {
            consolidated = createExtraAndClaim(traceId, spanId);
            extra = ensureMutable(extra);
            extra.add(consolidated);
        }
        if (extra == context.extra()) {
            return context;
        }
        return contextWithExtra(context, Collections.unmodifiableList(extra));
    }

    // TODO: this is internal. If we ever expose it otherwise, we should use Lists.ensureImmutable
    protected TraceContext contextWithExtra(TraceContext context, List<Object> immutableExtra) {
        return InternalPropagation.instance.withExtra(context, immutableExtra);
    }
}

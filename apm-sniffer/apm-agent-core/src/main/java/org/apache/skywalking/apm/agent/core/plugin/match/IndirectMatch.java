package org.apache.skywalking.apm.agent.core.plugin.match;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * All implementations can't direct match the class like {@link NameMatch} did.
 *
 */
public interface IndirectMatch extends ClassMatch {

    ElementMatcher.Junction buildJunction();

    boolean isMatch(TypeDescription typeDescription);

}

package io.jenkins.plugins;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.kohsuke.stapler.DataBoundConstructor;

public class DingTalkParamNotify implements Describable<DingTalkParamNotify> {

    private static final long serialVersionUID = 1L;

    private final String parameterName;

    @DataBoundConstructor
    public DingTalkParamNotify(final String parameterName) {
        this.parameterName = parameterName;
    }

    public String getParameterName() {
        return parameterName;
    }

    protected HashCodeBuilder addToHashCode(final HashCodeBuilder builder) {
        return builder.append(parameterName);
    }

    protected EqualsBuilder addToEquals(final EqualsBuilder builder, final DingTalkParamNotify that) {
        return builder.append(parameterName, that.parameterName);
    }

    protected ToStringBuilder addToToString(final ToStringBuilder builder) {
        return builder.append("parameterName", parameterName);
    }

    public boolean equals(final Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        return addToEquals(new EqualsBuilder(), (DingTalkParamNotify) that).isEquals();
    }

    public int hashCode() {
        return addToHashCode(new HashCodeBuilder()).toHashCode();
    }

    public String toString() {
        return addToToString(new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)).toString();
    }

    @Override
    public Descriptor<DingTalkParamNotify> getDescriptor() {
        return Jenkins.get().getDescriptorByType(ParamNotifyDescriptor.class);
    }

    @Extension
    public static class ParamNotifyDescriptor extends Descriptor<DingTalkParamNotify> {

        @Override
        public String getDisplayName() {
            return "Parameterized notifying";
        }
    }
}

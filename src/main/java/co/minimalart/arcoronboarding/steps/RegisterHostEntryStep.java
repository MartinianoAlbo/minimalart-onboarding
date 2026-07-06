package co.minimalart.arcoronboarding.steps;

import co.minimalart.arcoronboarding.domain.*;
import co.minimalart.arcoronboarding.infra.HostsFile;

/** Adds "127.0.0.1 arcorencasa.local" to the OS hosts file. If it can't (no elevation),
 * it does not fail the run — it logs the exact line for the developer to add manually. */
public final class RegisterHostEntryStep implements OnboardingStep {

    private final HostsFile hostsFile;
    private final String hostname;

    public RegisterHostEntryStep(HostsFile hostsFile, String hostname) {
        this.hostsFile = hostsFile;
        this.hostname = hostname;
    }

    @Override
    public String name() {
        return "Register host entry";
    }

    @Override
    public void validate(OnboardingContext ctx) {
        // No pre-validation: this step degrades gracefully at execution time.
    }

    @Override
    public void execute(OnboardingContext ctx, ProgressReporter progress) {
        try {
            if (hostsFile.addEntry(hostname)) {
                progress.log("Added '127.0.0.1 " + hostname + "' to " + hostsFile.path());
            } else {
                progress.log("Host entry for " + hostname + " already present.");
            }
        } catch (Exception e) {
            progress.log("Could not edit " + hostsFile.path() + " (" + e.getMessage() + ").");
            progress.log("Add this line manually with admin rights: 127.0.0.1 " + hostname);
        }
    }
}

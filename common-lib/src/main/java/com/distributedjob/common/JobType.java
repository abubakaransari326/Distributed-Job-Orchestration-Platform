package com.distributedjob.common;

/**
 * Kind of work to perform. Handlers in the worker are chosen from this value.
 */
public enum JobType {

    /** Simulated email send; executed inside the worker (with optional random failure for tests). */
    EMAIL,

    /** Long-running mock computation; executed inside the worker. */
    REPORT,

    /** Outbound call to an external HTTP endpoint; completion may arrive via callback. */
    EXTERNAL
}

# Phase 8 DLQ replay

Replay only after the underlying provider or scheduler fault is understood. The consumer uses bounded SQS redelivery; retryable outcomes remain visible and non-retryable or malformed messages are deleted. It does not claim exactly-once or single-delivery processing.

Records in the current database schema remain until an explicit, authorized operational cleanup removes them. There is no documented bounded retention window for idempotency, receipt, or attempt records.

1. Capture the DLQ message ID, body, attributes, receipt handle, reminder ID, scheduler version, and idempotency key. Do not edit the body or key.
2. Resolve the DLQ identity with `aws sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn RedriveAllowPolicy` and the source identity with `aws sqs get-queue-attributes --queue-url "$SOURCE_URL" --attribute-names QueueArn RedrivePolicy`. Confirm the source queue's `RedrivePolicy.deadLetterTargetArn` equals `$DLQ_ARN`; if the DLQ has a `RedriveAllowPolicy`, confirm it permits the intended `$SOURCE_ARN`. Stop if either identity or policy is unexpected.
3. Confirm the reminder state and `notification_attempt` history in RDS. Do not replay a message whose reminder is already `DELIVERED` or `ACKNOWLEDGED`.
4. Resolve the provider/configuration fault and verify the provider timeout/error is no longer occurring. Stop if the fault is not understood or the target/provider is not approved.
5. Obtain explicit approval for the eligible DLQ backlog and replay scope, record the approved bounded rate, and use the guarded SQS redrive operation: `aws sqs start-message-move-task --source-arn "$DLQ_ARN" --destination-arn "$SOURCE_ARN" --max-number-of-messages-per-second "$RATE"`. The task applies to the eligible DLQ backlog; it does not select one individual message, and a rate of 1 does not mean one message. Reconfirm both ARNs immediately before running it.
6. Monitor the move task, source queue, application logs, `notification_attempt`, and reminder status. Stop the task with `aws sqs cancel-message-move-task --source-arn "$DLQ_ARN" --task-handle "$TASK_HANDLE"` and investigate if the wrong queue is addressed, messages move faster than the approved rate, errors recur, or the reminder state changes unexpectedly.
7. If the message reaches the DLQ again, stop replaying. Record the message ID, error classification, attempt history, queue identities, and operator decision. Do not delete or reset persisted records as part of replay.

Scheduler failures are recovered by `schedule_outbox` reconciliation. Failed rows retry with bounded attempts and retain `last_error`; rows at the attempt limit require operator review before a controlled replay/reset.

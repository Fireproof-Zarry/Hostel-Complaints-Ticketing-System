// Pure helpers for optimistic upvote updates on the community dashboard

/** The complaint as it should look right after the student clicks Upvote. */
export function toggleUpvoteOptimistically(complaint) {
  const upvoted = !complaint.upvotedByYou;
  return {
    ...complaint,
    upvotedByYou: upvoted,
    upvoteCount: Math.max(0, (complaint.upvoteCount ?? 0) + (upvoted ? 1 : -1)),
  };
}

/** Replaces the complaint with the given id, leaving the others untouched. */
export function replaceComplaint(complaints, id, update) {
  return complaints.map((complaint) => (complaint.id === id ? update(complaint) : complaint));
}

package id.segari.ortools.dto;

public record ApplicationInfoDTO(
        String commitId,
        String buildAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String commitId;
        private String buildAt;

        public Builder commitId(String commitId) {
            this.commitId = commitId;
            return this;
        }

        public Builder buildAt(String buildAt) {
            this.buildAt = buildAt;
            return this;
        }

        public ApplicationInfoDTO build() {
            return new ApplicationInfoDTO(commitId, buildAt);
        }
    }
}

package com.moewadeul.publicdata.application;

import java.util.List;

public record SyncJobRunPage(List<SyncJobRunView> content, int page, int size, boolean hasNext) {}

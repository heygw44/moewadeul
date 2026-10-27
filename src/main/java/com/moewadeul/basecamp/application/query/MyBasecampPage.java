package com.moewadeul.basecamp.application.query;

import java.util.List;

public record MyBasecampPage(List<MyBasecampItem> content, int page, int size, boolean hasNext) {}

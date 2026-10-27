package com.moewadeul.program.application.catalog;

import java.util.List;

public record MyProgramApplicationPage(List<MyProgramApplicationItem> content, int page, int size, boolean hasNext) {}

package com.moewadeul.publicdata.infra.gocamping;

import java.util.List;

public record GoCampingPage(int totalCount, int pageNo, int numOfRows, List<GoCampingItem> items) {

    public GoCampingPage {
        items = List.copyOf(items);
    }
}

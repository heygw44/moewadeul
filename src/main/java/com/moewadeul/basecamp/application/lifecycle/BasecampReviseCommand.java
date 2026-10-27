package com.moewadeul.basecamp.application.lifecycle;

public record BasecampReviseCommand(
        String title, String description, BasecampOpenCommand.JoinConditionCommand joinCondition, Integer capacity) {}

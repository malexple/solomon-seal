package ru.mcs.llmrobot.core.tools;

public enum ToolPermission {
    READ_ONLY,   // read_file, grep, glob, list_tree, find_symbol, search_code
    WRITE,       // write_file, edit_file, delete_file
    EXEC         // bash, web_fetch
}
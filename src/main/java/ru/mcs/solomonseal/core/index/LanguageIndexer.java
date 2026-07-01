package ru.mcs.solomonseal.core.index;

import java.nio.file.Path;
import java.util.List;

/**
 * Интерфейс парсера языка.
 * Новый язык = новый класс, реализующий этот интерфейс.
 */
public interface LanguageIndexer {
    /** Идентификатор языка: "java", "kotlin", "python" */
    String language();

    /** Расширения файлов этого языка */
    List<String> fileExtensions();

    /** Парсит файл и возвращает список символов */
    List<SymbolRecord> index(Path file) throws Exception;
}
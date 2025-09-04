package ru.apache_maven.model;

/**
 * Класс, представляющий игрока в системе.
 * Соответствует таблице "players" в базе данных.
 */
public class Player {
    private int id;
    private String name;
    private int teamId;
    private String position;
    private int rating;
    private PlayerCategory category;  // Изменено с categoryId на category (строка)
    private String photo;

    // Конструктор по умолчанию
    public Player() {}

    /**
     * Полный конструктор для создания объекта игрока.
     *
     * @param id         ID игрока (автоинкремент в базе данных)
     * @param name       Имя игрока
     * @param teamId     ID команды
     * @param position   Позиция на поле
     * @param rating     Рейтинг игрока
     * @param category   Категория игрока (например, "Bronze", "Silver", "Gold")
     * @param photo      Путь к фотографии
     */
    public Player(int id, String name, int teamId, String position, int rating, PlayerCategory category, String photo) {
        this.id = id;
        this.name = name;
        this.teamId = teamId;
        this.position = position;
        this.rating = rating;
        this.category = category;
        this.photo = photo;
    }

    // Конструктор без ID (используется при добавлении нового игрока)
    public Player(String name, int teamId, String position, int rating, PlayerCategory category, String photo) {
        this(0, name, teamId, position, rating, category, photo);  // ID будет установлен базой данных
    }

    // Геттеры и сеттеры с валидацией
    public int getId() {
        return id;
    }

    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID игрока не может быть отрицательным");
        }
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя игрока не может быть пустым");
        }
        this.name = name;
    }

    public int getTeamId() {
        return teamId;
    }

    public void setTeamId(int teamId) {
        if (teamId <= 0) {
            throw new IllegalArgumentException("ID команды должен быть положительным");
        }
        this.teamId = teamId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        if (position == null || position.trim().isEmpty()) {
            throw new IllegalArgumentException("Позиция не может быть пустой");
        }
        this.position = position;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        if (rating < 0 || rating > 100) {
            throw new IllegalArgumentException("Рейтинг должен быть в диапазоне 0-100");
        }
        this.rating = rating;
    }

    public PlayerCategory getCategory() {
        return category;
    }

    public void setCategory(PlayerCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("Категория игрока не может быть null");
        }
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Название категории не может быть пустым");
        }
        this.category = category;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        if (photo == null || photo.trim().isEmpty()) {
            throw new IllegalArgumentException("Путь к фото не может быть пустым");
        }
        this.photo = photo;
    }

    // Дополнительные методы
    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", teamId=" + teamId +
                ", position='" + position + '\'' +
                ", rating=" + rating +
                ", category='" + category + '\'' +
                ", photo='" + photo + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return id == player.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
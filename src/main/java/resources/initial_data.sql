-- Лиги
INSERT INTO leagues (name) VALUES
                               ('EPL'),
                               ('La Liga'),
                               ('Serie A'),
                               ('Bundesliga'),
                               ('Ligue 1');

-- Категории игроков
INSERT INTO player_categories (name, weight, points) VALUES
                                                         ('Legend', 1, 2500),
                                                         ('Greatest', 5, 1500),
                                                         ('Diamond', 14, 700),
                                                         ('Gold', 25, 250),
                                                         ('Silver', 45, 100);

-- Команды
INSERT INTO teams (name, league_id) VALUES
                                        ('Real Madrid', 2),
                                        ('Barcelona', 2),
                                        ('Manchester City', 1),
                                        ('Bayern Munich', 4);

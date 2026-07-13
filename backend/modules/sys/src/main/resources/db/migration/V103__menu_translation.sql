CREATE TABLE IF NOT EXISTS sys_menu_translation (
    id            BIGSERIAL PRIMARY KEY,
    menu_id       BIGINT NOT NULL,
    locale        VARCHAR(10) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(64),
    updated_by    VARCHAR(64),
    UNIQUE(menu_id, locale)
);
COMMENT ON TABLE sys_menu_translation IS '菜单多语言翻译';
CREATE INDEX IF NOT EXISTS idx_menu_translation_locale ON sys_menu_translation(locale);

INSERT INTO sys_menu_translation (menu_id, locale, display_name)
SELECT id, 'zh-CN', menu_name FROM sys_menu;

INSERT INTO sys_menu_translation (menu_id, locale, display_name) VALUES
(1,'en','System'),(2,'en','Users'),(3,'en','Add User'),(4,'en','Edit User'),
(5,'en','Delete User'),(6,'en','Reset Password'),(7,'en','Assign Roles'),
(8,'en','Unlock User'),(10,'en','Roles'),(11,'en','Add Role'),
(12,'en','Edit Role'),(13,'en','Delete Role'),(14,'en','Permissions'),
(20,'en','Menus'),(21,'en','Add Menu'),(22,'en','Edit Menu'),(23,'en','Delete Menu'),
(30,'en','Units'),(31,'en','Add Unit'),(32,'en','Edit Unit'),(33,'en','Delete Unit'),
(40,'en','Config'),(41,'en','Add Config'),(42,'en','Edit Config'),
(50,'en','Sessions'),(51,'en','Audit Log'),(55,'en','Publish');

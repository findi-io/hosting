use ghost;
INSERT IGNORE INTO `matomo_user_token_auth` (`idusertokenauth`,`login`,`description`,`password`,`hash_algo`,`system_token`,`last_used`,`date_created`,`date_expired`) VALUES (2,'demo','tracking','{{.Values.matomo.password}}','sha512',0,NULL,'2023-01-30 19:37:25',NULL);

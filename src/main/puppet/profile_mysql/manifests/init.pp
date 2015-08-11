class profile_mysql {

  class { '::mysql::server':
  root_password => '';
  }


  mysql::db { 'mydb':
    user     => 'mydbuser',
    password => 'mypass',
    host     => 'localhost'
  }

}
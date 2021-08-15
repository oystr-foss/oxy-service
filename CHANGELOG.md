# Changelog
Starting at v2.1.0. =P
Dates are formatted as 'dd-MM-yyyy'.

## Release v2.1.0
LTS: 15-08-2021
  - Using distributed caching (Redis for now);
  - Removing useless 'GET /service/random' endpoint;
  - Adding 'role' concept to start working in clustered environments (leader election, cluster awareness and split brain resolving are yet to be done when and if needed.).


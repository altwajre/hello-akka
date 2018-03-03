# HTTPIE
```bash
http POST localhost:5000/events/RHCP tickets:=10
http POST localhost:5000/events/DjMadlib tickets:=15
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=2
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=8
http GET localhost:5000/events
http POST localhost:5000/events/RHCP/tickets tickets:=1
```

# Heroku preparation
```bash
brew install heroku/brew/heroku
heroku login
heroku create

sbt clean compile stage
heroku local

git init
heroku git:remote -a shielded-mesa-18089
```

# Heroku deployment
```bash
git add .
git commit -am "make it better"
git push heroku master
```

# Test on Heroku
```bash
http POST https://shielded-mesa-18089.herokuapp.com/events/RHCP tickets:=10
http POST https://shielded-mesa-18089.herokuapp.com/events/DjMadlib tickets:=15
http GET https://shielded-mesa-18089.herokuapp.com/events
http POST https://shielded-mesa-18089.herokuapp.com/events/RHCP/tickets tickets:=2
http GET https://shielded-mesa-18089.herokuapp.com/events
http POST https://shielded-mesa-18089.herokuapp.com/events/RHCP/tickets tickets:=8
http GET https://shielded-mesa-18089.herokuapp.com/events
http POST https://shielded-mesa-18089.herokuapp.com/events/RHCP/tickets tickets:=1
```

------------------------------------------------------------------------------------------------------------------------
IGNORE ORIGINAL!!!

Heroku deployment
=================

Heroku normally expects the project to reside in the root of the git repo.
the source code for the up and running chapter is not in the root of the repo, so you need to use a different command to deploy to heroku:

    git subtree push --prefix chapter-up-and-running heroku master

This command has to be executed from the root of the git repo, not from within the chapter directory.
The git subtree command is not as featured as the normal push command, for instance, it does not provide a flag to force push,
and it does not support the <local-branch>:<remote-branch> syntax which you can use with git push:

    git push heroku my-localbranch:master

Which is normally used to deploy from a branch to heroku (pushing a branch to heroku master).
It is possible to nest commands though, so if you want to push from a branch you can do the following:

    git push heroku `git subtree split --prefix chapter-up-and-running my-local-branch`:master

Where *my-local-branch* is your local branch.
Forcing a push can be done by nesting commands as well:

    git push heroku `git subtree split --prefix chapter-up-and-running master`:master --force

The above pushes the changes in local master to heroku master.



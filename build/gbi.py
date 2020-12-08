#!/usr/bin/python3

import git
import time


class EmptyTag:
    def __init__(self):
        self.commit = None
        self.name   = None


class BuildInfo:
    def __init__(self, config):
        self.config = config
        self.repo   = git.Repo(config["repo"])
        self.branch = self.repo.active_branch
        self.head   = self.repo.head.reference.commit

    def get_tags(self):
        return sorted(self.repo.tags, key=lambda t: t.commit.committed_datetime)
    
    def get_latest_tag(self):
        tags = self.get_tags()
        if len(tags) > 0:
            return tags[-1]

        return EmptyTag()

    def is_main_branch(self):
        return self.config["main_branch"] == self.branch.name

    def get_label(self):
        latest_tag = self.get_latest_tag()

        if self.is_main_branch() and self.head == latest_tag.commit:
            return latest_tag.name

        now  = time.strftime("%Y%m%dT%H%M%S")
        hash = self.head.hexsha[:7]

        if self.config['with_date']:
            return f"{self.branch}-{hash}-{now}"

        return f"{self.branch}-{hash}"

    def print_info(self):
        latest = self.get_latest_tag()
        dirty  = self.repo.is_dirty(untracked_files = True)

        print(f"Repo   : {self.repo.remotes[0].url} (dirty: {dirty})")
        print(f"Branch : {self.branch} (main: {self.is_main_branch()})")
        print(f"Head   : {self.head}")
        print(f"Tag    : {latest.commit} (name: {latest.name})")
        print(f"Label  : {self.get_label()}")

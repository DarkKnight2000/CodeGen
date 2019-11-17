#include<stdio.h>
#include<bits/stdc++.h>
using namespace std;
struct temp
{
    int a,b,c;
    string d;
    bool e;
};

void sum(struct temp* b){
    b->a = 0;
    b->b = 0;
    b->e = true;
    b->d = "HIIIII";
}

int main(){
    int a,b;
    struct temp* b1;
    b1->e = !b1->e;
    a = b1->d.size();
    return 0;
}